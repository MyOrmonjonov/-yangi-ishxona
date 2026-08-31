package uz.taskapp.user;

public record UpdateUserPreferencesRequest(String uiLanguage, String theme, Boolean remindersEnabled) {
}