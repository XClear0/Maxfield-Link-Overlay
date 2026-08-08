package io.github.xclear0.maxfieldoverlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class PlanParserTest {
    @Test
    public void parsesMaxfieldTextOutput() throws Exception {
        String text = "Agent Linking Assignments: links should be made in this order\n\n"
                + "Link ; Agent ;   # ; Link Origin\n"
                + "                 # ; Link Destination\n\n"
                + "   1 ;     1 ;  36 ; 甜爱路致橡树 \n"
                + "             ;  16 : 甜爱路飞鸟集 \n\n"
                + "  13 ;     2 ;   0 ; The Kissing Stone \n"
                + "             ;   1 : Historic Jail House \n";

        List<LinkStep> steps = PlanParser.parseForTest(text);

        assertEquals(2, steps.size());
        assertEquals(1, steps.get(0).linkNumber);
        assertEquals(36, steps.get(0).originNumber);
        assertEquals("甜爱路致橡树", steps.get(0).originName);
        assertEquals("甜爱路飞鸟集", steps.get(0).destinationName);
        assertEquals(2, steps.get(1).agentNumber);
    }

    @Test
    public void parsesMaxfieldLooseCsvOutput() throws Exception {
        String csv = "LinkNum, Agent, OriginNum, OriginName, DestinationNum, DestinationName\n"
                + "1, 1, 3, Origin, with comma, 4, Destination, with comma\n";

        List<LinkStep> steps = PlanParser.parseForTest(csv);

        assertEquals(1, steps.size());
        assertEquals("Origin, with comma", steps.get(0).originName);
        assertEquals(4, steps.get(0).destinationNumber);
        assertEquals("Destination, with comma", steps.get(0).destinationName);
    }

    @Test
    public void parsesQuotedCsvOutput() throws Exception {
        String csv = "LinkNum, Agent, OriginNum, OriginName, DestinationNum, DestinationName\n"
                + "2,1,10,\"Origin, West\",11,\"Destination, East\"\n";

        List<LinkStep> steps = PlanParser.parseForTest(csv);

        assertEquals("Origin, West", steps.get(0).originName);
        assertEquals("Destination, East", steps.get(0).destinationName);
    }
}
