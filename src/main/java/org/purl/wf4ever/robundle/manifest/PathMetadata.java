package org.purl.wf4ever.robundle.manifest;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(value = { "file", "uri", "mediatype", "createdOn",
        "createdBy", "authoredOn", "authoredBy", "conformsTo", "bundledAs" })
public class PathMetadata {
	private Path file;
	private URI uri;
	private String mediatype;

	private FileTime createdOn;
	private Agent createdBy;
    
    private FileTime authoredOn;    
    private List<Agent> authoredBy = new ArrayList<>();
    
    private URI conformsTo;

    private Proxy bundledAs;

    protected PathMetadata() {
    }

    @JsonCreator
    public PathMetadata(String uriStr) {
        setUri(URI.create(uriStr));
    }
    
    public URI getConformsTo() {
        return conformsTo;
    }
    
    public Agent getCreatedBy() {
        return createdBy;
    }

    public FileTime getCreatedOn() {
        return createdOn;
    }

    public Path getFile() {
        return file;
    }

    @JsonIgnore
    @Deprecated
    public Path getFolder() {
    	Proxy bundledAs = getBundledAs();
		if (bundledAs == null) { return null; }    	
        return bundledAs.getFolder();
    }

    public String getMediatype() {
        return mediatype;
    }

    @JsonIgnore
    @Deprecated
    public URI getProxy() {
    	Proxy bundledAs = getBundledAs();
		if (bundledAs == null) { return null; }    	
        return bundledAs.getURI();
    }

    public URI getUri() {
        return uri;
    }
    
    public void setConformsTo(URI conformsTo) {
        this.conformsTo = conformsTo;
    }    

    public void setCreatedBy(Agent createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedOn(FileTime createdOn) {
        this.createdOn = createdOn;
    }

    public void setFile(Path file) {
        this.file = file;
    }

    @Deprecated
    public void setFolder(Path folder) {
    	getOrCreateBundledAs().setFolder(folder);
    }

    public void setMediatype(String mediatype) {
        this.mediatype = mediatype;
    }

    @Deprecated
    public void setProxy() {
        getOrCreateBundledAs().setURI();
    }

    @Deprecated
    public void setProxy(URI proxy) {
    	getOrCreateBundledAs().setURI(proxy);
    }

    public void setUri(URI uri) {
        this.uri = uri;
        if (! uri.isAbsolute()) {
            // TODO: How to create a Path without knowing the root?
//            file = uri;
//            this.uri = null;
        }
    }
    
    @Override
    public String toString() {
        if (getUri() != null) { 
            return getUri().toString();
        }
        if (getFile() != null) {
            return getFile().toString();
        }
        if (getProxy() != null) { 
            return getProxy().toString();
        }
        return "PathMetadata <null>";
    }

	public List<Agent> getAuthoredBy() {
		return authoredBy;
	}

	public void setAuthoredBy(List<Agent> authoredBy) {
		if (authoredBy == null) {
			throw new NullPointerException("authoredBy list can't be empty");
		}
		this.authoredBy = authoredBy;
	}

	public FileTime getAuthoredOn() {
		return authoredOn;
	}

	public void setAuthoredOn(FileTime authoredOn) {
		this.authoredOn = authoredOn;
	}

	@JsonIgnore
	public Proxy getOrCreateBundledAs() {
		if (bundledAs == null) {
			bundledAs = new Proxy();
			setProxy();
		}
		return bundledAs;
	}
	
	
	public Proxy getBundledAs() {
		return bundledAs;
	}

	public void setBundledAs(Proxy bundledAs) {
		if (bundledAs == null) { 
			throw new NullPointerException("bundledAs can't be empty (try a new Proxy instance)");
		}
		this.bundledAs = bundledAs;
	}


    

}
