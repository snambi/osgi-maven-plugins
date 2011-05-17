package com.ebay.osgi.maven.compiler.osgi;

public class ManifestNotFoundException extends Exception {

	private static final long serialVersionUID = 6080602188564245397L;

	public ManifestNotFoundException(String message){
		super(message);
	}
	
	public ManifestNotFoundException(Throwable error){
		super(error);
	}
	
	public ManifestNotFoundException( String message, Throwable error ){
		super( message, error);
	}
}
