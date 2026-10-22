package com.smartdroneinspection;

import java.util.Locale;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a real MinIO server for the integration suites and publishes its mapped endpoint to the
 * environment.
 *
 * <p>Storage is real on purpose. Evidence bytes and the published report deliverable travel through
 * the same {@code MinioEvidenceObjectStore} adapter that production uses, so bucket creation,
 * streaming put, streamed get, and delete are exercised rather than assumed. An in-memory map would
 * let a broken adapter pass every suite and fail only against a deployed environment.
 *
 * <p>This is an {@link ApplicationContextInitializer} rather than {@code @ServiceConnection} for
 * two reasons. First, {@code @ServiceConnection} resolves its properties through a registered
 * {@code ConnectionDetailsFactory}, and Spring Boot ships none for MinIO. Second, the port MinIO is
 * reachable on is assigned by Docker at startup, so the properties cannot be known until the
 * container is running; an initializer runs before context refresh and is therefore the first point
 * where the mapped port exists. Running as an initializer also keeps the value out of the
 * {@code @SpringBootTest} annotations, so no individual suite has to opt in.
 *
 * <p>The server is started once per JVM and reused across suites, matching how the shared
 * PostgreSQL container is shared, and is stopped by the JVM shutdown hook Testcontainers installs.
 */
public class MinioTestInfrastructure
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  static final String ACCESS_KEY = "test-only-minio-access";
  static final String SECRET_KEY = "test-only-minio-secret-key";
  private static final String BUCKET = "smartdroneinspection-test";
  // The official upstream is quay.io/minio/minio, which is also what docker-compose runs locally.
  // A GitHub runner cannot pull it anonymously, so this mirrors the exact bytes into this
  // organization's Docker Hub namespace and pins the digest, so no upstream re-tag can move it.
  // Source digest equals the cached quay.io/minio/minio:latest image.
  private static final String PINNED_MINIO =
      "tungbach12/minio@sha256:a1a8bd4ac40ad7881a245bab97323e18f971e4d4cba2c2007ec1bedd21cbaba2";
  // That mirror holds the amd64 bytes only, so an arm64 host cannot start it. Docker Hub's
  // minio/minio tag is a multi-arch manifest with a native arm64 image, which is what an ARM64
  // developer machine needs. The pinned digest stays the default so CI keeps the reproducible
  // image it was verified against; only a host that cannot run that image falls back.
  private static final String MULTI_ARCH_MINIO = "minio/minio:latest";

  private static final DockerImageName MINIO = resolveMinioImage();

  private static final GenericContainer<?> SERVER = startOnce();

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "minio-test-container",
                Map.of(
                    "app.storage.minio.enabled", "true",
                    "app.storage.minio.endpoint", endpoint(),
                    "app.storage.minio.bucket", BUCKET,
                    "app.storage.minio.access-key", ACCESS_KEY,
                    "app.storage.minio.secret-key", SECRET_KEY)));
  }

  /** The endpoint a MinIO client in this JVM should use. */
  static String endpoint() {
    SERVER.start();
    return "http://" + SERVER.getHost() + ":" + SERVER.getMappedPort(9000);
  }

  /**
   * Picks the image the current host can actually execute.
   *
   * <p>The pinned mirror is amd64-only, and a host without x86 emulation cannot start it. Rather
   * than failing every integration suite with a container launch error, an arm64 host uses the
   * multi-arch upstream tag, which resolves to a native arm64 image. The container still runs the
   * same MinIO server and the same bucket, so the storage contract under test is unchanged.
   */
  private static DockerImageName resolveMinioImage() {
    String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (architecture.contains("aarch64") || architecture.contains("arm64")) {
      return DockerImageName.parse(MULTI_ARCH_MINIO);
    }
    return DockerImageName.parse(PINNED_MINIO);
  }

  private static GenericContainer<?> startOnce() {
    return new GenericContainer<>(MINIO)
        .withExposedPorts(9000)
        .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
        .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
        .withCommand("server", "/data")
        .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));
  }
}
