/*
 * @author Scott C Sosna
 */

package dev.scottsosna.neo4j.gradle;

import dev.scottsosna.neo4j.gradle.node.Artifact;
import dev.scottsosna.neo4j.gradle.relationship.ConfigurationType;
import dev.scottsosna.neo4j.gradle.relationship.DependsOn;
import dev.scottsosna.neo4j.gradle.relationship.ResolutionType;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.cypher.BooleanOperator;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * OGM Intro for loading data
 */
public class DependencyLoader {

    /**
     *  Maps the different type of nodes by looking at the groupId and seeing if it starts with the
     *  same string as what is in artifactMapping[0]; if so, the type is artifactMapping[1]
     */
    private String[][] artifactMapping;

    /**
     * Session factory for connecting to Neo4j database
     */
    private final SessionFactory sessionFactory;

    //  Configuration info for connecting to the Neo4J database
    static private final String SERVER_URI = "bolt://localhost";
    static private final String SERVER_USERNAME = "neo4j";
    static private final String SERVER_PASSWORD = "password";

    //  Each level in gradle is prefixed with a character and 4 spaces, so always substring 5 characters for each
    //  level, either to go one dependency level deeper or to extract the artifact information
    static private final int GRADLE_LEVEL_WIDTH = 5;

    //  Important Gradle Strings that we need to look for
    static private final String ARTIFACT_SEPARATOR = ":";
    static private final String GRADLE_CLASSPATH = "Classpath";
    static private final String GRADLE_ARTIFACT_CURRENT_LEVEL = "+";
    static private final String GRADLE_ARTIFACT_LAST_LEVEL = "\\";
    static private final String GRADLE_ARTIFACT_NEXT_LEVEL = "|";
    static private final String GRADLE_RESOLVED_INDICATION = "->";
    static private final String GRADLE_ROOT_PROJECT = "Root project";

    //  Default artifact types that should always be present
    static private final String ARTIFACT_TYPE_EXTERNAL = "EXTERNAL";
    static private final String ARTIFACT_TYPE_INTERNAL = "INTERNAL";
    static private final String ARTIFACT_TYPE_PROJECT = "PROJECT";

    /**
     * Constructor
     */
    public DependencyLoader(String artifactMappingFile) {

        //  Define session factory for connecting to Neo4j database
        Configuration configuration = new Configuration.Builder().uri(SERVER_URI).credentials(SERVER_USERNAME, SERVER_PASSWORD).build();
        sessionFactory = new SessionFactory(configuration, "dev.scottsosna.neo4j.gradle.node", "dev.scottsosna.neo4j.gradle.relationship");

        if (artifactMappingFile != null) {
            //  Try and load the mappings from an external file.
            loadArtifactMapping(artifactMappingFile);
        }

        //  If nothing loaded - no file specified, file doesn't exist, whatever - then load some defaults/
        if (artifactMapping == null || artifactMapping.length == 0) {
            loadDefaultArtifactMapping();
        }
    }


    /**
     * Main entry point for program
     * @param args file or directory name for dependencies to load, directory will iterate through all found files.
     */
    public static void main (final String[] args) {

        //  Required to have at least one CLI argument which is either file or directory.
        if (args.length > 0) {

            //  Determine that the file or directory exists
            File argFile = new File(args[0]);
            if (argFile.exists()) {

                //  Create the loader instance and purge the database of the previous run.
                DependencyLoader loader = new DependencyLoader(args.length >= 2 ? args[1] : null);
                loader.purgeDatabase();

                //  If the CLI is a file, then processed individually; otherwise process all files in directory
                if (argFile.isFile()) {
                    loader.process(argFile);
                } else if (argFile.isDirectory()) {
                    for (File one: argFile.listFiles()) {
                        //  only process files in this directory, do not navigate deeper
                        if (one.isFile()) {
                            loader.process(one);
                        }
                    }
                }
            }
        } else {
            System.out.println ("File or directory name required.");
        }


        return;
    }

    /**
     * Always purge the Neo4J database to start fresh when loading the Gradle dependencies
     */
    private void purgeDatabase() {
        Session session = sessionFactory.openSession();
        session.purgeDatabase();
    }

    /**
     * Entry point for doing the work
     */
    private void process (final File file) {

        //  Open new Neo4J database session and process each file in its own transaction that can be rolled
        //  back, if necessary.
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();

        try (BufferedReader br = new BufferedReader (Files.newBufferedReader(file.toPath()))) {
            //  Files are processed line-by-line so collect the lines into a list that can be passed to
            //  worker method.  This decouples the input source from tghe actually loading of the data.
            load (br.lines().collect(Collectors.toList()), session);

            //  Dependencies successfully loaded, so commit the data.
            transaction.commit();

            System.out.println (file.getName() + " completed.");
        } catch (Exception e) {
            //  Something bad happen, log and rollback whatever might have been loaded before the exception.
            System.out.println("Exception: " + e);
            transaction.rollback();
        }

        //  Gotta close the session factory to shutdown Neo4J threads, allowing clean program exit.
        sessionFactory.close();
    }

    /**
     * Processes the output from the Gradle dependecy tree and loads into the Neo4J database
     * @param lines individual lines from the Gradle dependency tree
     * @param session Neo4J database session
     * @throws IOException thrown if something bad happens
     */
    private void load (final List<String> lines,
                       final Session session)throws IOException {

        //  A stack is used to track the dependee artifacts, as artifacts are created they're added
        //  the stack and popped off as needed when any/all dependendents are processed
        Stack<Artifact> stack = new Stack<>();

        AtomicReference<ConfigurationType> config = new AtomicReference<>(ConfigurationType.UNKNOWN);
        AtomicReference<String> projectName = new AtomicReference<>();

        //  Stream through the lines one-by-one
        lines.stream().forEach (line -> {
            ResolutionType rt;

            //  Try and extract "Root project" from the output
            if (line.startsWith (GRADLE_ROOT_PROJECT)) {
                String temp = line.substring(14, line.length() -1);
                projectName.set(temp);
                stack.push(promoteOrCreateProject(temp, session));
                return;
            } else if (line.contains (GRADLE_CLASSPATH)) {
                config.set(ConfigurationType.findByGradleString(line.substring(0, line.indexOf(GRADLE_CLASSPATH) + 9)));
                return;
            } else if (config.get() == ConfigurationType.UNKNOWN) {
                return;
            } else {
                //  Any line not starting with "+" or "|" can be skipped.
                if (!line.startsWith(GRADLE_ARTIFACT_CURRENT_LEVEL) && !line.startsWith(GRADLE_ARTIFACT_NEXT_LEVEL)) {
                    return;
                } else {
                    //  Determine the type of resolution.
                    rt = ResolutionType.determine(line);
                    if (rt == ResolutionType.SKIPPED) {
                        return;
                    }
                }
            }

            //  When the resolution type is not default/normal, strip from the end of the line.  Need to add "-1" for the
            //  space that always preceeds the resolution specification.
            if (rt != ResolutionType.NORMAL) {
                line = line.substring(0, line.length() - rt.getIdentifier().length() - 1);
            }

            //  Figure out which level we're at in the dependency tree: if first character is '+' or '\' we have an
            //  artifact to process
            int level = 1;
            while (!line.startsWith(GRADLE_ARTIFACT_CURRENT_LEVEL) && !line.startsWith(GRADLE_ARTIFACT_LAST_LEVEL)) {
                line = line.substring(GRADLE_LEVEL_WIDTH);
                level++;
            }

            //  When the stack size is greater than the level just identified, we've moved up one or more
            //  levels (i.e., the previous artifact was that leaf node in the dependency tree) so pop nodes.
            //
            //  When the stack size is the same as the level, we had two successive artifacts at the same
            //  level, indicating the previous artifact did not have any child dependencies.  Therefore,
            //  that one needs to be popped off the stack as well.
            while (level > 0 && stack.size() != level) {
                stack.pop();
            }

            //  Split the line into the artifact's constituent parts
            String[] parts = line.substring(GRADLE_LEVEL_WIDTH).split(ARTIFACT_SEPARATOR);
            String groupId = parts[0].trim();
            String artifactId = parts[1].trim();

            //  Either specified or resolved is always present, but possible to have either or both (3 combinations).
            String resolvedVersion = null;
            String specifiedVersion = null;
            if (parts.length == 2) {
                //  No explicit version, which should mean the artifact has the resolved version.
                String[] temp = artifactId.split(GRADLE_RESOLVED_INDICATION);
                if (temp.length == 2) {
                    artifactId = temp[0].trim();
                    specifiedVersion = temp[1].trim();
                }
            } else {
                String[] temp = parts[2].split(GRADLE_RESOLVED_INDICATION);
                specifiedVersion = temp[0].trim();
                if (temp.length == 2) {
                    resolvedVersion = temp[1].trim();
                }
            }

            //  Get the dependent artifact, which may already exist if used in previous dependency
            Artifact dependee = findOrCreateArtifact(groupId, artifactId, session);

            //  The stack's top node is the dependee artifact, which requires dependency resolution just
            //  found/created from the current line read.
            Artifact dependant = stack.peek();

            //  Find an existing or create a new relationship between the dependee and dependent artifact
            DependsOn dpon = findOrCreateDependsOn(dependee, dependant, resolvedVersion, specifiedVersion, session);
            dpon.addConfiguration(config.get());
            dpon.addResolutionType(rt);
            dpon.addSource(projectName.get());
            session.save(dpon);

            //  Push the new artifact on the stack.
            stack.push(dependee);
        });


        return;
    }


    /**
     * Based on the groupId, subtype the artifact (which becomes an artifact's label)
     * @param groupId the groupId from the artifact's fully-qualified name
     * @return the subtype of the artifact
     */
    private String determineArtifactType (final String groupId) {

        //  Iterate through our mappings and see if we find anything interesting.
        for (String[] one : artifactMapping) {
            if (groupId.startsWith(one[0])) return one[1];
        }

        //  If nothing else, the artifact is by default external.
        return ARTIFACT_TYPE_EXTERNAL;
//        if (groupId.startsWith("com.mrll") || groupId.startsWith("com.datasite")) return ARTIFACT_TYPE_INTERNAL;
//        else if (groupId.startsWith("org.springframework")) return "SPRING";
//        else if (groupId.startsWith("org.apache")) return "APACHE";
//        else return ARTIFACT_TYPE_EXTERNAL;
    }


    /**
     * Attempt to find an artifact based on artifact Id or group/artifact Id.
     * @param groupId the group id for the artifact
     * @param artifactId the artifact id for the artifact
     * @param session the current Neo4J database session
     * @return the artifacts found, if any
     */
    private Collection<Artifact> findArtifact (final String groupId,
                                               final String artifactId,
                                               final Session session) {

        //  Create filter based on artifact's groupId and artifactId (which is also stored as
        //  name).  The groupId is optional, providing a way to potentially find a project
        //  that has already been loaded as an internal artifact.
        Filters composite = new Filters();
        Filter filter = new Filter("artifactId", ComparisonOperator.EQUALS, artifactId);
        composite.add(filter);

        if (groupId != null) {
            filter = new Filter("groupId", ComparisonOperator.EQUALS, groupId);
            filter.setBooleanOperator(BooleanOperator.AND);
            composite.add(filter);
        }


        //  Execute query and return collection to caller.
        return session.loadAll(Artifact.class, composite);
    }


    /**
     * Find an existing artifact in the Neo4j database or create a new one.  An individual artifact may
     * be referenced multiple times in a dependency tree, so only needs to be created once but then
     * reused for multiple dependencies
     * @param groupId the group id for the artifact
     * @param artifactId the artifact id for the artifact
     * @param session the current Neo4J database session
     * @return the artifact either found or created
     */
    private Artifact findOrCreateArtifact (final String groupId,
                                           final String artifactId,
                                           final Session session) {

        Artifact toReturn;

        //  Attempt to find the artifact by group and artifact id.
        Collection<Artifact> artifacts = findArtifact(groupId, artifactId, session);
        if (!artifacts.isEmpty()) {
            //  Something found, just use the first in the stream (there should only be 1)
            toReturn = artifacts.stream().findFirst().get();
        } else {
            //  Unfortunately, the gradle dependencies does not have the group id for a project, just project name
            //  which is used as artifact name.  Depending on order, the project may be created first, in which case
            //  we can use that as the artifact and just update the group id.
            artifacts = findArtifact(ARTIFACT_TYPE_PROJECT, artifactId, session);
            if (!artifacts.isEmpty()) {
                //  Found a project to use, add the group id that we now have.
                toReturn = artifacts.stream().findFirst().get();
                toReturn.setGroupId(groupId);
                session.save(toReturn);
            } else {
                //  Need to create a new artifact node, immediately save to Neo4J
                toReturn = new Artifact(groupId, artifactId, determineArtifactType(groupId));
                session.save(toReturn);
            }
        }


        return toReturn;
    }


    /**
     * Find an existing dependency based on the dependee/dependant artifact and the version of the dependency.
     * @param dependee the artifact that provides the dependency
     * @param dependent the artifact that requires a dependency
     * @param resolvedVersion the version resolved, when different from the specified
     * @param specifiedVersion the specified version of the dependent
     * @param session Neo4J database session
     * @return 0 or more dependency relationships, really should only get 1 if something/anything found
     */
    private Iterable<DependsOn> findDependsOn (final Artifact dependee,
                                               final Artifact dependent,
                                               final String resolvedVersion,
                                               final String specifiedVersion,
                                               final Session session) {
        Map<String, Object> params = new HashMap<>();
        params.put("dgroup", dependent.getGroupId());
        params.put("dname", dependent.getName());
        params.put("dogroup", dependee.getGroupId());
        params.put("doname", dependee.getName());
        params.put ("specifiedVersion", specifiedVersion);
        params.put ("resolvedVersion", resolvedVersion);

        /**
         * There are three possibilities:
         * 1) the build.gradle version specified is ultimately what is the dependency
         * 2) the build.gradle version specified is resolved to a different version by gradle
         * 3) no version specified but is resolved to a specific version by gradle.
         */
        String cypher;
        if (resolvedVersion == null) {
            cypher = "MATCH (d:Artifact {groupId: $dgroup, name: $dname})-[dpo:DEPENDS_ON {specifiedVersion: $specifiedVersion}]->(do:Artifact {groupId: $dogroup, name: $doname}) RETURN dpo";
        } else if (specifiedVersion == null) {
            cypher = "MATCH (d:Artifact {groupId: $dgroup, name: $dname})-[dpo:DEPENDS_ON {resolvedVersion: $resolvedVersion}]->(do:Artifact {groupId: $dogroup, name: $doname}) RETURN dpo";
        } else {
            cypher = "MATCH (d:Artifact {groupId: $dgroup, name: $dname})-[dpo:DEPENDS_ON {specifiedVersion: $specifiedVersion, resolvedVersion: $resolvedVersion}]->(do:Artifact {groupId: $dogroup, name: $doname}) RETURN dpo";
        }

        return session.query (DependsOn.class, cypher, params);
    }


    /**
     * Either find an existing dependency or create a new one.
     * @param dependee the artifact that provides the dependency
     * @param dependent the artifact that requires a dependency
     * @param resolvedVersion the version resolved, when different from the specified
     * @param specifiedVersion the specified version of the dependent
     * @param session Neo4J database session
     * @return 0 or more dependency relationships, really should only get 1 if something/anything found
     */
    private DependsOn findOrCreateDependsOn (final Artifact dependee,
                                             final Artifact dependent,
                                             final String resolvedVersion,
                                             final String specifiedVersion,
                                             final Session session) {
        DependsOn toReturn;
        Iterator<DependsOn> it = findDependsOn (dependee, dependent, resolvedVersion, specifiedVersion, session).iterator();
        if (it.hasNext()) {
            toReturn = it.next();
        } else {
            toReturn = new DependsOn (dependee, dependent, resolvedVersion, specifiedVersion);
            session.save (toReturn);
        }


        return toReturn;
    }

    /**
     * Either find an existing internal node that represents a project or create a brand-new project node.
     * @param projectName project name extracted from the dependency file
     * @param session Neo4J database session
     * @return new or replaced project node.
     */
    private Artifact promoteOrCreateProject(final String projectName,
                                            final Session session) {

        Artifact toReturn = null;

        //  For a multi-project dependency load, it's possible that the project has already been created as a basic
        //  artifact.  Unfortunately, we can only map a project name to an artifactId, so do a search and see if
        //  an internal artifact node is found which can be promoted to a project.
        for (Artifact one: findArtifact(null, projectName, session)) {

            //  If an internal node is found, assume that's we want and change labels to make it project.
            if (one.getLabels().remove(ARTIFACT_TYPE_INTERNAL)) {
                one.getLabels().add(ARTIFACT_TYPE_PROJECT);
                one.setArtifactType(ARTIFACT_TYPE_PROJECT);
                toReturn = one;
                break;
            }
        }

        //  If nothing or nothing relevant found, create a new project node.
        if (toReturn == null) {
            //  Nothing found, so create a new project node.
            toReturn = new Artifact(ARTIFACT_TYPE_PROJECT, projectName, ARTIFACT_TYPE_PROJECT);
        }

        //  Always have something to save, either a new or changed artifact node.
        session.save (toReturn);


        return toReturn;
    }

    /**
     * Attempt to load the artifact type mappings from an external file
     * @param fileName file in properties format "org.apache=APACHE" or whatever
     */
    private void loadArtifactMapping(final String fileName) {

        if (fileName != null) {
            File file = new File(fileName);
            if (file.exists() && file.isFile()) {
                List<String[]> mappings = new ArrayList<>();
                try (BufferedReader br = Files.newBufferedReader(file.toPath())) {
                    br.lines().forEach (one -> {

                        //  Ignore comments and empty lines
                        if (one.length() > 0 && !one.startsWith("#")) {
                            mappings.add(one.split("="));
                        }
                    });

                    //  Convert into raw array for (hopefully) faster processing than a Java collection
                    artifactMapping = mappings.toArray(new String[mappings.size()][2]);
                } catch (Exception e) {
                    System.out.println ("Exception processing artifact mappings: " + e);
                    artifactMapping = null;
                }
            }
        }
    }

    /**
     * If nothing external defined, load with the most common mappings to figure out node type
     */
    private void loadDefaultArtifactMapping() {
        List<String[]> mappings = new ArrayList<>();
        mappings.add(new String[] {"com.mrll", ARTIFACT_TYPE_INTERNAL});
        mappings.add(new String[] {"com.datasite", ARTIFACT_TYPE_INTERNAL});
        mappings.add(new String[] {"org.springframework", "SPRING"});
        mappings.add(new String[] {"io.pivotal", "SPRING"});
        mappings.add(new String[] {"org.apache", "APACHE"});

        //  Convert into raw array for (hopefully) faster processing than a Java collection
        artifactMapping = mappings.toArray(new String[mappings.size()][2]);
    }
}
