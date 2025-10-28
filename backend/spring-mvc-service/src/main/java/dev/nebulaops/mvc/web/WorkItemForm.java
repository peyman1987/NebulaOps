package dev.nebulaops.mvc.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WorkItemForm {
    @NotBlank(message = "Il titolo è obbligatorio")
    @Size(max = 120, message = "Il titolo non può superare 120 caratteri")
    private String title;

    @Size(max = 1000, message = "La descrizione non può superare 1000 caratteri")
    private String description;

    @NotBlank(message = "Il proprietario è obbligatorio")
    @Size(max = 80, message = "Il proprietario non può superare 80 caratteri")
    private String owner;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
