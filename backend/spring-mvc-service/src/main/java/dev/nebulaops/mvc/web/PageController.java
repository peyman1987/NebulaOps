package dev.nebulaops.mvc.web;

import dev.nebulaops.mvc.config.AppProperties;
import dev.nebulaops.mvc.domain.WorkItemStatus;
import dev.nebulaops.mvc.service.WorkItemService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PageController {
    private final WorkItemService service;
    private final AppProperties properties;

    public PageController(WorkItemService service, AppProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @ModelAttribute("app")
    public AppProperties appProperties() {
        return properties;
    }

    @GetMapping({"/", "/work-items"})
    public String index(Model model) {
        model.addAttribute("items", service.findAll());
        model.addAttribute("form", new WorkItemForm());
        model.addAttribute("statuses", WorkItemStatus.values());
        return "index";
    }

    @PostMapping("/work-items")
    public String create(@Valid @ModelAttribute("form") WorkItemForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("items", service.findAll());
            model.addAttribute("statuses", WorkItemStatus.values());
            return "index";
        }
        var created = service.create(form.getTitle(), form.getDescription(), form.getOwner());
        redirectAttributes.addFlashAttribute("message", "Item created: " + created.title());
        return "redirect:/work-items";
    }

    @GetMapping("/work-items/{id}")
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("item", service.findById(id));
        model.addAttribute("statuses", WorkItemStatus.values());
        return "detail";
    }

    @PostMapping("/work-items/{id}/status")
    public String changeStatus(@PathVariable String id,
                               @RequestParam WorkItemStatus status,
                               RedirectAttributes redirectAttributes) {
        var updated = service.changeStatus(id, status);
        redirectAttributes.addFlashAttribute("message", "Status updated: " + updated.status());
        return "redirect:/work-items/" + id;
    }

    @PostMapping("/work-items/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        service.delete(id);
        redirectAttributes.addFlashAttribute("message", "Item deleted");
        return "redirect:/work-items";
    }
}
