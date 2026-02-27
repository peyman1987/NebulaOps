package com.apiforge.service;

import com.apiforge.model.ApiModels.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

@Service
public class StorageService {
    private final ObjectMapper mapper;
    private final Path dir;
    private List<CollectionDto> collections = new ArrayList<>();
    private List<EnvironmentDto> envs = new ArrayList<>();
    private List<HistoryDto> history = new ArrayList<>();

    public StorageService(ObjectMapper mapper, @Value("${APIFORGE_DATA_DIR:./data}") String dataDir) {
        this.mapper = mapper;
        this.dir = Paths.get(dataDir);
    }

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(dir);
        load();
        saveAll();
    }

    private <T> T read(String file, TypeReference<T> type, T fallback) {
        try {
            Path p = dir.resolve(file);
            if (Files.exists(p)) return mapper.readValue(p.toFile(), type);
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void write(String file, Object value) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(file).toFile(), value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void load() {
        collections = read("collections.json", new TypeReference<>() {
        }, new ArrayList<>());
        envs = read("environments.json", new TypeReference<>() {
        }, new ArrayList<>());
        history = read("history.json", new TypeReference<>() {
        }, new ArrayList<>());
    }

    public synchronized void saveAll() {
        write("collections.json", collections);
        write("environments.json", envs);
        write("history.json", history);
    }

    public List<CollectionDto> collections() {
        return collections;
    }

    public List<EnvironmentDto> envs() {
        return envs;
    }

    public List<HistoryDto> history() {
        return history.stream().sorted(Comparator.comparing(HistoryDto::timestamp).reversed()).limit(200).toList();
    }

    public synchronized CollectionDto saveCollection(CollectionDto c) {
        var now = Instant.now();
        var n = new CollectionDto(c.id() == null || c.id().isBlank() ? UUID.randomUUID().toString() : c.id(), c.name(), c.description(), c.requests() == null ? new ArrayList<>() : c.requests(), c.createdAt() == null ? now : c.createdAt(), now);
        collections.removeIf(x -> x.id().equals(n.id()));
        collections.add(n);
        saveAll();
        return n;
    }

    public synchronized void deleteCollection(String id) {
        collections.removeIf(c -> c.id().equals(id));
        saveAll();
    }

    public synchronized HistoryDto addHistory(HttpRequestDto req, HttpResponseDto res) {
        var h = new HistoryDto(UUID.randomUUID().toString(), req, res, Instant.now());
        history.add(h);
        if (history.size() > 500) history = history.subList(history.size() - 500, history.size());
        saveAll();
        return h;
    }
}
