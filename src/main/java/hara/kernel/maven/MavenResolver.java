package hara.kernel.maven;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import hara.kernel.maven.RepositorySystemFactory;

public class MavenResolver {

  private final RepositorySystem system;
  private final DefaultRepositorySystemSession session;
  private final List<RemoteRepository> repositories;

  public MavenResolver() {
    this.system = RepositorySystemFactory.newRepositorySystem();
    this.session = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepo =
        new LocalRepository(System.getProperty("user.home") + "/.m2/repository");
    this.session.setLocalRepositoryManager(
        this.system.newLocalRepositoryManager(this.session, localRepo));
    this.repositories =
        List.of(
            new RemoteRepository.Builder(
                    "central", "default", "https://repo.maven.apache.org/maven2/")
                .build());
  }

  public List<File> resolve(String coordinate) throws DependencyResolutionException {
    Artifact artifact = new DefaultArtifact(coordinate);
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(artifact, ""));
    collectRequest.setRepositories(this.repositories);

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setCollectRequest(collectRequest);

    List<ArtifactResult> artifactResults =
        this.system.resolveDependencies(this.session, dependencyRequest).getArtifactResults();

    return artifactResults.stream()
        .map(ArtifactResult::getArtifact)
        .map(Artifact::getFile)
        .collect(Collectors.toList());
  }
}
