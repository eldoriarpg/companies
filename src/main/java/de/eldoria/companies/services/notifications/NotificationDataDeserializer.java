package de.eldoria.companies.services.notifications;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import de.eldoria.eldoutilities.localization.Replacement;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class NotificationDataDeserializer implements JsonDeserializer<NotificationData> {
    @Override
    public NotificationData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        List<Replacement> replacements = new ArrayList<>();

        for (var element : json.getAsJsonObject().get("replacements").getAsJsonArray()) {
            var object = element.getAsJsonObject();
            replacements.add(Replacement.create(object.get("key").getAsString(), object.get("value").getAsString()));
        }

        return new NotificationData(MessageType.valueOf(json.getAsJsonObject().get("type").getAsString()),
                json.getAsJsonObject().get("message").getAsString(), replacements.toArray(new Replacement[0]));
    }
}
