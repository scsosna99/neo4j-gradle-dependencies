/**
 * @author Scott C Sosna
 */
package dev.scottsosna.neo4j.gradle.relationship;

import dev.scottsosna.neo4j.gradle.node.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A Neo4J relationship representing a marriage
 */
@RelationshipEntity(type = "DEPENDS_ON")
public class DependsOn {

    /**
     * Internal Neo4J id of the node
     */
    @Id
    @GeneratedValue
    private Long id;

    @EndNode
    private Artifact dependee;

    @StartNode
    private Artifact dependant;

    private Set<ConfigurationType> configurations;

    private Set<ResolutionType> resolutionType;

    private Set<String> sources;

    private String name;

    private String specifiedVersion;

    private String resolvedVersion;

    /**
     * Constructor
     */
    public DependsOn() {}

    public DependsOn(final Artifact dependee,
                     final Artifact dependant,
                     final String resolvedVersion,
                     final String specifiedVersion) {
        this.dependee = dependee;
        this.dependant = dependant;
        this.resolvedVersion = resolvedVersion;
        this.specifiedVersion = specifiedVersion;
        if (resolvedVersion == null) {
            this.name = specifiedVersion;
        } else if (specifiedVersion == null) {
            this.name = " -> " + resolvedVersion;
        } else {
            this.name = specifiedVersion + " -> " + resolvedVersion;
        }
        configurations = new HashSet();
        resolutionType = new HashSet();
        sources = new HashSet<>();
    }

    public void addConfiguration (final ConfigurationType config) {
        configurations.add(config);
    }

    public void addResolutionType (final ResolutionType rt) {
        resolutionType.add(rt);
    }

    public void addSource (final String source) {sources.add(source);}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Artifact getDependee() {
        return dependee;
    }

    public void setDependee(Artifact dependee) {
        this.dependee = dependee;
    }

    public Artifact getDependant() {
        return dependant;
    }

    public void setDependant(Artifact dependant) {
        this.dependant = dependant;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecifiedVersion() {
        return specifiedVersion;
    }

    public void setSpecifiedVersion(String specifiedVersion) {
        this.specifiedVersion = specifiedVersion;
    }

    public String getResolvedVersion() {
        return resolvedVersion;
    }

    public void setResolvedVersion(String resolvedVersion) {
        this.resolvedVersion = resolvedVersion;
    }

    public Set<ConfigurationType> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Set<ConfigurationType> configurations) {
        this.configurations = configurations;
    }

    public Set<ResolutionType> getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(Set<ResolutionType> resolutionType) {
        this.resolutionType = resolutionType;
    }

    public Set<String> getSources() {
        return sources;
    }

    public void setSources(Set<String> sources) {
        this.sources = sources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DependsOn dependsOn = (DependsOn) o;

        return new EqualsBuilder().append(dependee, dependsOn.dependee).append(dependant, dependsOn.dependant).append(name, dependsOn.name).append(specifiedVersion, dependsOn.specifiedVersion).append(resolvedVersion, dependsOn.resolvedVersion).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(dependee).append(dependant).append(name).append(specifiedVersion).append(resolvedVersion).toHashCode();
    }
}
