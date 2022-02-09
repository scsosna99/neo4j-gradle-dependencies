/**
 * @author Scott C Sosna
 */
package dev.scottsosna.neo4j.gradle.relationship;

public enum ResolutionType {

    CONSTRAINED ("(c)", "gradle.constrained.included"),
    OMITTED ("(*)", "gradle.omitted.included"),
    NORMAL(null, null),
    NOT_RESOLVED ("(n)", "gradle.notresolved.included"),
    SKIPPED (null, null);

    final boolean enabled;

    /**
     * The identifier at the end of a Gradle dependency that indicates the type of dependency.
     */
    final String identifier;

    ResolutionType (final String identifier,
                    final String propertyName) {
        this.identifier = identifier;
        enabled = propertyName != null ? Boolean.valueOf(System.getProperty(propertyName)) : true;
    }

    /**
     * getter
     * @return boolean whether the resolution type is enabled for this run
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * getter
     * @return the string that occurs at the end of a dependency to identify a specific resolution type
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Determine the special resolution type, if any based on the end of a dependency line.  If the
     * special type is not enabled, return that is should be skipped
     * @param line dependency type
     * @return resolution type to use for dependency when creating database graph
     */
    public static ResolutionType determine (final String line) {

        for (ResolutionType one : ResolutionType.values()) {
            //  Normal is default so don't attempt to process.
            if (one.identifier != null) {
                if (line != null && line.substring(line.length() - 3).endsWith(one.identifier)) {
                    return one.enabled ? one : SKIPPED;
                }
            }
        }

        return NORMAL;
    }
}
