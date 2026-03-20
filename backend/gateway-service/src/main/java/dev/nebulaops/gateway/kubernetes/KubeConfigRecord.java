package dev.nebulaops.gateway.kubernetes;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "kubernetes_kubeconfigs")
public class KubeConfigRecord {
    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;
    private String contextName;
    private String clusterName;
    private String server;
    private String namespace;
    private String kubeconfig;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContextName() { return contextName; }
    public void setContextName(String contextName) { this.contextName = contextName; }
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    public String getServer() { return server; }
    public void setServer(String server) { this.server = server; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getKubeconfig() { return kubeconfig; }
    public void setKubeconfig(String kubeconfig) { this.kubeconfig = kubeconfig; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
