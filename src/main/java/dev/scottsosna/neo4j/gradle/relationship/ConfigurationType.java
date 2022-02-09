/**
 * @author Scott C Sosna
 */
package dev.scottsosna.neo4j.gradle.relationship;

/**
 * The Gradle configurations recognized and available for loading dependencies into Neo4J.
 */
public enum ConfigurationType {

    COMPILE ("compileClasspath"),
    RUNTIME ("runtimeClasspath"),
    TEST_COMPILE ("testCompileClasspath"),
    TEST_RUNTIME ("testRuntimeClasspath"),
    UNKNOWN ("unknown");

    /**
     * Gradle indicates for which configuration the dependency tree is being generated.  The configuration can be
     * a standard Gradle configuration or a custom one defined by the engineer.  The configuration is loaded into
     * Neo4J so we can determine which configuration brought in the specific dependency.
     */
    final String configurationId;

    /**
     * Constructor
     * @param configurationId
     */
    ConfigurationType (final String configurationId) {
        this.configurationId = configurationId;
    }

    /**
     * Determine the configuration based on the potential identifier name from the input file.  If the identifier
     * isn't recognized, then an UNKNOWN configuration type is returned, which causes the dependency tree to be
     * skipped.
     * @param configurationId
     * @return the discovered configuration type or UNKNOWN
     */
    public static ConfigurationType findByGradleString (final String configurationId) {
        for (ConfigurationType one : ConfigurationType.values()) {
            if (one.configurationId.equals(configurationId)) return one;
        }

        return UNKNOWN;
    }
}
