package iped.app.home.config;/*
                             * @created 22/09/2022
                             * @project IPED
                             * @author Thiago S. Figueiredo
                             */

import java.util.ArrayList;
import java.util.Arrays;

import iped.app.ui.Messages;

public enum Languages {

    PT_BR(0, Messages.get("Home.LanguagePtBR"), "pt-BR"), EN(1, Messages.get("Home.LanguageEN"), "en"), IT(2, Messages.get("Home.LanguageIT"), "it-IT"), DE(3, Messages.get("Home.LanguageDE"), "de-DE"), ES(4,
            Messages.get("Home.LanguageEsAr"), "es-AR");

    private int value;
    private String description;
    private String languageTag;

    Languages(int value, String description, String languageTag) {
        this.value = value;
        this.description = description;
        this.languageTag = languageTag;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public void setLanguageTag(String languageTag) {
        this.languageTag = languageTag;
    }

    public static ArrayList<String> getListOfDescription() {
        ArrayList<String> listOfDescriptions = new ArrayList<>();
        Arrays.stream(Languages.values()).forEach(languages -> listOfDescriptions.add(languages.getDescription()));
        return listOfDescriptions;
    }

    public static Languages getByLanguageTag(String languageTag) {
        for (Languages language : Languages.values()) {
            if (language.getLanguageTag().equalsIgnoreCase(languageTag))
                return language;
        }
        return null;
    }

    @Override
    public String toString() {
        return description;
    }
}
