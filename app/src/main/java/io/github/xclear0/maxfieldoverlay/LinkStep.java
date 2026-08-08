package io.github.xclear0.maxfieldoverlay;

import org.json.JSONException;
import org.json.JSONObject;

public final class LinkStep {
    public final int linkNumber;
    public final int agentNumber;
    public final int originNumber;
    public final String originName;
    public final int destinationNumber;
    public final String destinationName;

    public LinkStep(
            int linkNumber,
            int agentNumber,
            int originNumber,
            String originName,
            int destinationNumber,
            String destinationName) {
        this.linkNumber = linkNumber;
        this.agentNumber = agentNumber;
        this.originNumber = originNumber;
        this.originName = originName.trim();
        this.destinationNumber = destinationNumber;
        this.destinationName = destinationName.trim();
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("linkNumber", linkNumber);
        json.put("agentNumber", agentNumber);
        json.put("originNumber", originNumber);
        json.put("originName", originName);
        json.put("destinationNumber", destinationNumber);
        json.put("destinationName", destinationName);
        return json;
    }

    static LinkStep fromJson(JSONObject json) throws JSONException {
        return new LinkStep(
                json.getInt("linkNumber"),
                json.getInt("agentNumber"),
                json.getInt("originNumber"),
                json.getString("originName"),
                json.getInt("destinationNumber"),
                json.getString("destinationName"));
    }
}
