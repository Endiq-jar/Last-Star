import com.laststar.game.gl.ModelLibrary;

/** Loads the baked model set for tests, or returns null when it is not built. */
final class ModelLoaderFixture {
    private static ModelLibrary cached;
    private static boolean tried;

    private ModelLoaderFixture() {
    }

    static ModelLibrary load() {
        if (!tried) {
            tried = true;
            try {
                cached = ModelLibrary.load(
                        new java.io.FileInputStream("app/src/main/assets/models/world.bin"));
            } catch (Exception e) {
                cached = null;
            }
        }
        return cached;
    }
}
