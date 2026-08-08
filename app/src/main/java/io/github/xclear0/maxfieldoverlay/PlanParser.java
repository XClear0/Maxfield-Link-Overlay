package io.github.xclear0.maxfieldoverlay;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlanParser {
    private static final int MAX_CHARACTERS = 5_000_000;
    private static final int MAX_STEPS = 10_000;

    private static final Pattern TEXT_ORIGIN = Pattern.compile(
            "^\\s*(\\d+)\\s*;\\s*(\\d+)\\s*;\\s*(\\d+)\\s*;\\s*(.*?)\\s*$");
    private static final Pattern TEXT_DESTINATION = Pattern.compile(
            "^\\s*;\\s*(\\d+)\\s*[:;]\\s*(.*?)\\s*$");
    private static final Pattern LOOSE_CSV = Pattern.compile(
            "^\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(.*?)\\s*,\\s*(\\d+)\\s*,\\s*(.*?)\\s*$");

    private PlanParser() {}

    public static List<LinkStep> parse(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("无法打开所选文件");
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                content.append(buffer, 0, count);
                if (content.length() > MAX_CHARACTERS) {
                    throw new IOException("规划文件超过 5 MB，无法导入");
                }
            }
        }

        String text = removeBom(content.toString());
        List<LinkStep> steps;
        if (looksLikeCsv(text)) {
            steps = parseCsv(text);
            if (steps.isEmpty()) {
                steps = parseText(text);
            }
        } else {
            steps = parseText(text);
            if (steps.isEmpty()) {
                steps = parseCsv(text);
            }
        }

        if (steps.isEmpty()) {
            throw new IOException(
                    "没有找到 Link。请选择 Maxfield 输出的 agent_assignments.txt、"
                            + "agent_N_assignment.txt 或 agent_assignments.csv");
        }
        if (steps.size() > MAX_STEPS) {
            throw new IOException("规划超过 10000 条 Link，无法导入");
        }
        return steps;
    }

    static List<LinkStep> parseForTest(String text) throws IOException {
        return parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean looksLikeCsv(String text) {
        String firstLine = text.split("\\R", 2)[0].toLowerCase();
        return firstLine.contains("linknum") && firstLine.contains("originname");
    }

    private static List<LinkStep> parseText(String text) {
        List<LinkStep> result = new ArrayList<>();
        PendingOrigin pending = null;

        for (String rawLine : text.split("\\R")) {
            String line = removeBom(rawLine);
            Matcher origin = TEXT_ORIGIN.matcher(line);
            if (origin.matches()) {
                pending = new PendingOrigin(
                        parseInt(origin.group(1)),
                        parseInt(origin.group(2)),
                        parseInt(origin.group(3)),
                        origin.group(4).trim());
                continue;
            }

            Matcher destination = TEXT_DESTINATION.matcher(line);
            if (pending != null && destination.matches()) {
                String destinationName = destination.group(2).trim();
                if (!pending.name.isEmpty() && !destinationName.isEmpty()) {
                    result.add(new LinkStep(
                            pending.link,
                            pending.agent,
                            pending.portal,
                            pending.name,
                            parseInt(destination.group(1)),
                            destinationName));
                }
                pending = null;
            }
        }
        return result;
    }

    private static List<LinkStep> parseCsv(String text) {
        List<LinkStep> result = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = removeBom(rawLine).trim();
            if (line.isEmpty() || line.toLowerCase().startsWith("linknum")) {
                continue;
            }

            List<String> fields = splitQuotedCsv(line);
            if (fields.size() == 6
                    && isInteger(fields.get(0))
                    && isInteger(fields.get(1))
                    && isInteger(fields.get(2))
                    && isInteger(fields.get(4))) {
                addCsvStep(result, fields);
                continue;
            }

            // Current Maxfield output is comma-separated but does not quote portal
            // names. This fallback preserves ordinary commas inside either name.
            Matcher loose = LOOSE_CSV.matcher(line);
            if (loose.matches()) {
                List<String> matched = new ArrayList<>(6);
                for (int i = 1; i <= 6; i++) {
                    matched.add(loose.group(i));
                }
                addCsvStep(result, matched);
            }
        }
        return result;
    }

    private static void addCsvStep(List<LinkStep> result, List<String> fields) {
        String origin = fields.get(3).trim();
        String destination = fields.get(5).trim();
        if (!origin.isEmpty() && !destination.isEmpty()) {
            result.add(new LinkStep(
                    parseInt(fields.get(0)),
                    parseInt(fields.get(1)),
                    parseInt(fields.get(2)),
                    origin,
                    parseInt(fields.get(4)),
                    destination));
        }
    }

    private static List<String> splitQuotedCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == ',' && !quoted) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(value);
            }
        }
        fields.add(field.toString().trim());
        return fields;
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private static String removeBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static final class PendingOrigin {
        final int link;
        final int agent;
        final int portal;
        final String name;

        PendingOrigin(int link, int agent, int portal, String name) {
            this.link = link;
            this.agent = agent;
            this.portal = portal;
            this.name = name;
        }
    }
}
