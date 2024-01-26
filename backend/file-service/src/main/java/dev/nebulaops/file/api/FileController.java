package dev.nebulaops.file.api;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import dev.nebulaops.file.domain.FileMetadata;
import dev.nebulaops.file.repo.FileMetadataRepository;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileMetadataRepository repo;

    public FileController(FileMetadataRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public FileMetadata upload(@RequestParam String organizationId, @RequestParam String taskId, @RequestPart MultipartFile file) {
        return repo.save(new FileMetadata(null, organizationId, taskId, file.getOriginalFilename(), file.getContentType(), file.getSize(), "local/" + file.getOriginalFilename(), Instant.now()));
    }

    @GetMapping
    public List<FileMetadata> list() {
        return repo.findAll();
    }
}
