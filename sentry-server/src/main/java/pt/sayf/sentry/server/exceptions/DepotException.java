package pt.sayf.sentry.server.exceptions;

public class DepotException extends Exception{

	private static final long serialVersionUID = -8711500861183184251L;
	
	private String depotPath;
	
	public DepotException(String path, String cause){
		super(cause);
		depotPath = path;
	}
	
	public String getPath() {
		return depotPath;
	}

}
