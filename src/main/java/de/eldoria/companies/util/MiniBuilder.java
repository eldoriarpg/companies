package de.eldoria.companies.util;

import de.eldoria.eldoutilities.localization.MessageComposer;

public class MiniBuilder {
    public static String localizedClickButton(String type, String command, String localeCode) {
        return MessageComposer.create().text("<click:%s:%s>[$%s$]</click>", type, command, localeCode).build();
    }
}
