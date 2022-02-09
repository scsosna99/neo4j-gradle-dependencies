/*
 * @author Scott C Sosna
 */
package dev.scottsosna.neo4j.gradle.node;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Labels;
import org.neo4j.ogm.annotation.NodeEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Neo4J node representing an artifact listed in a Gradle dependency tree
 */
@NodeEntity
public class Artifact {
    @Id
    @GeneratedValue
    private Long id;

    @Labels
    Set<String> labels;

    /**
     * The artifact id from the artifact's fully-qualified identifier.
     */
    private String artifactId;

    /**
     * The group id from the artifact's fully-qualified identifier
     */
    private String groupId;

    /**
     * Name is often the default property that is displayed when visualizing, so putting something readable is
     * useful.  For our purposes, the artifact id will be used as the node name.
     */
    private String name;

    /**
     * The type of artifact; technically not required because Labels should do the same thing, but it helps
     * with some visualization tools such as neovis.
     */
    private String artifactType;

    /**
     * Default constructor
     */
    public Artifact() {}

    /**
     * Constructor
     * @param groupId artifact's groupId
     * @param artifactIdOrName artifact's artifactId or some other useful name
     * @param artifactType a subtype of the artifact that will be mapped to the node's label
     */
    public Artifact(final String groupId,
                    final String artifactIdOrName,
                    final String artifactType) {

        this.groupId = groupId;
        this.artifactId = artifactIdOrName;
        this.name = artifactIdOrName;
        this.artifactType = artifactType;
        labels = new HashSet<>();
        labels.add(artifactType);
    }

    /**
     * @return artifact's artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
      * @param artifactId
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return artifact's groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @return node's labels representing the artifact's type
     */
    public Set<String> getLabels() {
        return labels;
    }

    /**
     * @param labels node's labels
     */
    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    /**
     * @return the node's unique identifier
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the node's unique identifier
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return node's name, usually the artifactId
     */
    public String getName() {
        return name;
    }

    /**
     * @param name name to assign to the node
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the node's artifact type
     */
    public String getArtifactType() {
        return artifactType;
    }

    /**
     * setter
     * @param artifactType artifact type
     */
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Artifact artifact = (Artifact) o;

        return new EqualsBuilder().append(labels, artifact.labels).append(artifactId, artifact.artifactId).append(groupId, artifact.groupId).append(name, artifact.name).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(labels).append(artifactId).append(groupId).append(name).toHashCode();
    }
}