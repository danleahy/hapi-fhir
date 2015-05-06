package ca.uhn.fhir.android;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.rest.client.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;

public class BuiltJarIT {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(BuiltJarIT.class);

	@Test
	public void testParser() {
		FhirContext ctx = FhirContext.forDstu2();
		
		Patient p = new Patient();
		p.addIdentifier().setSystem("system");
		
		String str = ctx.newXmlParser().encodeResourceToString(p);
		Patient p2 = ctx.newXmlParser().parseResource(Patient.class, str);
		
		assertEquals("system", p2.getIdentifierFirstRep().getSystemElement().getValueAsString());
	}
	
	/**
	 * A simple client test - We try to connect to a server that doesn't exist, but
	 * if we at least get the right exception it means we made it up to the HTTP/network stack
	 * 
	 * Disabled for now - TODO: add the old version of the apache client (the one that
	 * android uses) and see if this passes
	 */
	public void testClient() {
		FhirContext ctx = FhirContext.forDstu2();
		try {
			IGenericClient client = ctx.newRestfulGenericClient("http://127.0.0.1:44442/SomeBase");
			client.conformance();
		} catch (FhirClientConnectionException e) {
			// this is good
		}
	}
	
	/**
	 * Android does not like duplicate entries in the JAR
	 */
	@Test
	public void testJarContents() throws Exception {
		String wildcard = "hapi-fhir-android-*.jar";
		Collection<File> files = FileUtils.listFiles(new File("target"), new WildcardFileFilter(wildcard), null);
		if (files.isEmpty()) {
			throw new Exception("No files matching " + wildcard);
		}

		for (File file : files) {
			ourLog.info("Testing file: {}", file);

			ZipFile zip = new ZipFile(file);
			
			int totalClasses = 0;
			int totalMethods = 0;
			TreeSet<ClassMethodCount> topMethods = new TreeSet<ClassMethodCount>();
			
			try {
				Set<String> names = new HashSet<String>();
				for (Enumeration<? extends ZipEntry> iter = zip.entries(); iter.hasMoreElements();) {
					ZipEntry next = iter.nextElement();
					String nextName = next.getName();
					if (!names.add(nextName)) {
						throw new Exception("File " + file + " contains duplicate contents: " + nextName);
					}
					
					if (nextName.contains("$") == false) {
						if (nextName.endsWith(".class")) {
							String className = nextName.replace("/", ".").replace(".class", "");
							try {
							Class<?> clazz = Class.forName(className);
							int methodCount = clazz.getMethods().length;
							topMethods.add(new ClassMethodCount(className, methodCount));
							totalClasses++;
							totalMethods += methodCount;
							} catch (NoClassDefFoundError e) {
								// ignore
							} catch (ClassNotFoundException e) {
								// ignore
							}
						}
					}
				}
				
				ourLog.info("File {} contains {} entries", file, names.size());
				ourLog.info("Total classes {} - Total methods {}", totalClasses, totalMethods);
				ourLog.info("Top classes {}", new ArrayList<ClassMethodCount>(topMethods).subList(topMethods.size() - 10, topMethods.size()));
				
			} finally {
				zip.close();
			}
		}
	}

	private static class ClassMethodCount implements Comparable<ClassMethodCount> {

		private String myClassName;
		private int myMethodCount;
		
		public ClassMethodCount(String theClassName, int theMethodCount) {
			myClassName = theClassName;
			myMethodCount = theMethodCount;
		}

		@Override
		public String toString() {
			return myClassName + "[" + myMethodCount + "]";
		}

		@Override
		public int compareTo(ClassMethodCount theO) {
			return myMethodCount - theO.myMethodCount;
		}

		public String getClassName() {
			return myClassName;
		}

		public void setClassName(String theClassName) {
			myClassName = theClassName;
		}

		public int getMethodCount() {
			return myMethodCount;
		}

		public void setMethodCount(int theMethodCount) {
			myMethodCount = theMethodCount;
		}
		
	}
	
}