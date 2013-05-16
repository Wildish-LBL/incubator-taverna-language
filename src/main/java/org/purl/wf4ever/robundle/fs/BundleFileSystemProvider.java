package org.purl.wf4ever.robundle.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BundleFileSystemProvider extends FileSystemProvider {
	private static final String APPLICATION_VND_WF4EVER_ROBUNDLE_ZIP = "application/vnd.wf4ever.robundle+zip";
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final String WIDGET = "widget";

	/**
	 * Public constructor provided for FileSystemProvider.installedProviders().
	 * Use #getInstance() instead.
	 *  
	 * @deprecated
	 */
	@Deprecated
	public BundleFileSystemProvider() {
    }
	
	@Override
	public boolean equals(Object obj) {
	    return getClass() == obj.getClass();
	} 
	
	@Override
	public int hashCode() {
	    return getClass().hashCode();
	}
	
	protected static void addMimeTypeToZip(ZipOutputStream out, String mimetype)
			throws IOException {
		if (mimetype == null) {
			mimetype = APPLICATION_VND_WF4EVER_ROBUNDLE_ZIP;
		}
		// FIXME: Make the mediatype a parameter
		byte[] bytes = mimetype.getBytes(UTF8);

		// We'll have to do the mimetype file quite low-level
		// in order to ensure it is STORED and not COMPRESSED

		ZipEntry entry = new ZipEntry("mimetype");
		entry.setMethod(ZipEntry.STORED);
		entry.setSize(bytes.length);
		CRC32 crc = new CRC32();
		crc.update(bytes);
		entry.setCrc(crc.getValue());

		out.putNextEntry(entry);
		out.write(bytes);
		out.closeEntry();
	}

	protected static void createBundleAsZip(Path bundle, String mimetype)
			throws FileNotFoundException, IOException {
		// Create ZIP file as
		// http://docs.oracle.com/javase/7/docs/technotes/guides/io/fsp/zipfilesystemprovider.html
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(
				bundle, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING))) {
			addMimeTypeToZip(out, mimetype);
		}
	}
	
	private static class Singleton {
	    private static final BundleFileSystemProvider INSTANCE = new BundleFileSystemProvider();
	}
	
	public static BundleFileSystemProvider getInstance() {
		for (FileSystemProvider provider : FileSystemProvider
				.installedProviders()) {
			if (provider instanceof BundleFileSystemProvider) {
				return (BundleFileSystemProvider) provider;
			}
		}
		// Fallback for OSGi environments
		return Singleton.INSTANCE;
	}

	public static BundleFileSystem newFileSystemFromExisting(Path bundle)
			throws FileNotFoundException, IOException {
		URI w;
		try {
			w = new URI("widget", bundle.toUri().toASCIIString(), null);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Can't create widget: URI for "
					+ bundle);
		}
		FileSystem fs = FileSystems.newFileSystem(w,
				Collections.<String, Object> emptyMap());
		return (BundleFileSystem) fs;
	}

	public static BundleFileSystem newFileSystemFromNew(Path bundle)
			throws FileNotFoundException, IOException {
		return newFileSystemFromNew(bundle,
				APPLICATION_VND_WF4EVER_ROBUNDLE_ZIP);
	}

	public static BundleFileSystem newFileSystemFromNew(Path bundle,
			String mimetype) throws FileNotFoundException, IOException {
		createBundleAsZip(bundle, mimetype);
		return newFileSystemFromExisting(bundle);
	}

	public static BundleFileSystem newFileSystemFromTemporary() throws IOException {
		Path bundle = Files.createTempFile("robundle", ".zip");
		BundleFileSystem fs = BundleFileSystemProvider.newFileSystemFromNew(
				bundle, null);
		return fs;
	}

    /**
     * The list of open file systems. This is static so that it is shared across
     * eventual multiple instances of this provider (such as when running in an
     * OSGi environment). Access to this map should be synchronized to avoid
     * opening a file system that is not in the map.
     */
	protected static Map<URI, WeakReference<BundleFileSystem>> openFilesystems = new HashMap<>();

	protected URI baseURIFor(URI uri) {
		if (!(uri.getScheme().equals(WIDGET))) {
			throw new IllegalArgumentException("Unsupported scheme in: " + uri);
		}
		if (!uri.isOpaque()) {
			return uri.resolve("/");
		}
		Path localPath = localPathFor(uri);
		Path realPath;
		try {
			realPath = localPath.toRealPath();
		} catch (IOException ex) {
			realPath = localPath.toAbsolutePath();
		}
		// Generate a UUID from the MD5 of the URI of the real path (!)
		UUID uuid = UUID.nameUUIDFromBytes(realPath.toUri().toASCIIString()
				.getBytes(UTF8));
		try {
			return new URI(WIDGET, uuid.toString(), "/", null);
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Can't create widget:// URI for: "
					+ uuid);
		}
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		origProvider(path).checkAccess(fs.unwrap(path), modes);
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options)
			throws IOException {
		BundleFileSystem fs = (BundleFileSystem) source.getFileSystem();
		origProvider(source)
				.copy(fs.unwrap(source), fs.unwrap(target), options);
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs)
			throws IOException {
		BundleFileSystem fs = (BundleFileSystem) dir.getFileSystem();
		origProvider(dir).createDirectory(fs.unwrap(dir), attrs);
	}

	@Override
	public void delete(Path path) throws IOException {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		origProvider(path).delete(fs.unwrap(path));
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path,
			Class<V> type, LinkOption... options) {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		return origProvider(path).getFileAttributeView(fs.unwrap(path), type,
				options);
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		BundlePath bpath = (BundlePath) path;
		return bpath.getFileSystem().getFileStore();
	}

    @Override
    public BundleFileSystem getFileSystem(URI uri) {
        synchronized (openFilesystems) {
            URI baseURI = baseURIFor(uri);
            WeakReference<BundleFileSystem> ref = openFilesystems.get(baseURI);
            if (ref == null) {
                throw new FileSystemNotFoundException(uri.toString());
            }
            BundleFileSystem fs = ref.get();
            if (fs == null) {
                openFilesystems.remove(baseURI);
                throw new FileSystemNotFoundException(uri.toString());
            }
            return fs;
        }
    }

	@Override
	public Path getPath(URI uri) {
		BundleFileSystem fs = getFileSystem(uri);
		Path r = fs.getRootDirectory();
		if (uri.isOpaque()) {
			return r;
		} else {
			return r.resolve(uri.getPath());
		}
	}

	@Override
	public String getScheme() {
		return WIDGET;
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		return origProvider(path).isHidden(fs.unwrap(path));
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		return origProvider(path).isSameFile(fs.unwrap(path), fs.unwrap(path2));
	}

	private Path localPathFor(URI uri) {
		URI localUri = URI.create(uri.getSchemeSpecificPart());
		return Paths.get(localUri);
	}

	@Override
	public void move(Path source, Path target, CopyOption... options)
			throws IOException {
		BundleFileSystem fs = (BundleFileSystem) source.getFileSystem();
		origProvider(source)
				.copy(fs.unwrap(source), fs.unwrap(target), options);
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path,
			Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		final BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		return origProvider(path).newByteChannel(fs.unwrap(path), options,
				attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir,
			final Filter<? super Path> filter) throws IOException {
		final BundleFileSystem fs = (BundleFileSystem) dir.getFileSystem();
		final DirectoryStream<Path> stream = origProvider(dir)
				.newDirectoryStream(fs.unwrap(dir), new Filter<Path>() {
					@Override
					public boolean accept(Path entry) throws IOException {
						return filter.accept(fs.wrap(entry));
					}
				});
		return new DirectoryStream<Path>() {
			@Override
			public void close() throws IOException {
				stream.close();
			}

			@Override
			public Iterator<Path> iterator() {
				return fs.wrapIterator(stream.iterator());
			}
		};
	}

	@Override
	public BundleFileSystem newFileSystem(URI uri, Map<String, ?> env)
			throws IOException {

		Path localPath = localPathFor(uri);
		URI baseURI = baseURIFor(uri);

		FileSystem origFs = FileSystems.newFileSystem(localPath, null);

		BundleFileSystem fs;
		synchronized (openFilesystems) {
			WeakReference<BundleFileSystem> existingRef = openFilesystems
					.get(baseURI);
			if (existingRef != null) {
				BundleFileSystem existing = existingRef.get();
				if (existing != null && existing.isOpen()) {
					throw new FileSystemAlreadyExistsException(
							baseURI.toASCIIString());
				}
			}
			fs = new BundleFileSystem(origFs, baseURI);
			openFilesystems.put(baseURI,
					new WeakReference<BundleFileSystem>(fs));
		}
		return fs;
	}

	private FileSystemProvider origProvider(Path path) {
		return ((BundlePath) path).getFileSystem().origFS.provider();
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path,
			Class<A> type, LinkOption... options) throws IOException {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		return origProvider(path)
				.readAttributes(fs.unwrap(path), type, options);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes,
			LinkOption... options) throws IOException {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		return origProvider(path).readAttributes(fs.unwrap(path), attributes,
				options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value,
			LinkOption... options) throws IOException {
		BundleFileSystem fs = (BundleFileSystem) path.getFileSystem();
		origProvider(path).setAttribute(fs.unwrap(path), attribute, value,
				options);
	}

}
