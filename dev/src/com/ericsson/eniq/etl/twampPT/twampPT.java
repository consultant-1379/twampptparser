package com.ericsson.eniq.etl.twampPT;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.distocraft.dc5000.etl.parser.Main;
import com.distocraft.dc5000.etl.parser.MeasurementFile;
import com.distocraft.dc5000.etl.parser.Parser;
import com.distocraft.dc5000.etl.parser.SourceFile;
import com.ericsson.eniq.common.ENIQEntityResolver;

public class twampPT extends DefaultHandler implements Parser {
	private Logger log;
	private MeasurementFile measFile = null;
	private Map<String, String> measData;
	private SourceFile sourceFile;

	// ***************Data related variables**********************
	private String nodeName = null;
	private String type = null;
	private String interfaceName = null;
	private String key = null;
	private String defaultTagMask = null;
	private String tagID = null;
	private Map<String, String> addrMaskMap = null;
	private String charValue = null;

	// ***************** Worker stuff ****************************
	private String techPack;
	private String setType;
	private String setName;
	private String workerName = "";
	private int status = 0;

	private Main mainParserObject = null;

	public void init(Main main, String techPack, String setType, String setName, String workerName) {
		this.mainParserObject = main;
		this.techPack = techPack;
		this.setType = setType;
		this.setName = setName;
		this.status = 1;
		this.workerName = workerName;

		String logWorkerName = "";
		if (workerName.length() > 0)
			logWorkerName = "." + workerName;

		log = Logger.getLogger("etl." + techPack + "." + setType + "." + setName + ".parser.twampPT" + logWorkerName);

	}

	public int status() {
		return status;
	}

	public void run() {

		try {
			this.status = 2;
			SourceFile sf = null;
			while ((sf = mainParserObject.nextSourceFile()) != null) {

				try {
					mainParserObject.preParse(sf);
					parse(sf, techPack, setType, setName);
					mainParserObject.postParse(sf);
				} catch (Exception e) {
					mainParserObject.errorParse(e, sf);
				} finally {
					mainParserObject.finallyParse(sf);
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Worker parser failed to parse", e.getMessage());
		} finally {
			this.status = 3;
		}
	}

	// ***************** Worker stuff ****************************

	public void parse(SourceFile sf, String techPack, String setType, String setName) throws Exception {
		measData = new HashMap<String, String>();
		addrMaskMap = new HashMap<String, String>();
		this.sourceFile = sf;
		String filename = sf.getName();
		defaultTagMask = sf.getProperty("vendorIDMask", "(.*)(-)(.*)(-)(.*)([+,-])(.*)");
		tagID = parseFileName(filename, defaultTagMask);
		final XMLReader xmlReader = new org.apache.xerces.parsers.SAXParser();
		xmlReader.setContentHandler(this);
		xmlReader.setErrorHandler(this);
		xmlReader.setEntityResolver(new ENIQEntityResolver(log.getName()));
		xmlReader.parse(new InputSource(sf.getFileInputStream()));
		log.fine("Parsing Completed");
	}

	/**
	 * Event handlers
	 */
	public void startDocument() {
		log.finest("Start document");
	}

	public void endDocument() throws SAXException {

		log.finest("End document");
		measData.clear();
		sourceFile = null;
	}

	public void startElement(String uri, String name, String qName, Attributes atts) throws SAXException {
		if (qName.equals("Node")) {
			for (int i = 0; i < atts.getLength(); i++) {
				if (atts.getLocalName(i).equalsIgnoreCase("name")) {
					nodeName = atts.getValue(i);
				}
				if (atts.getLocalName(i).equalsIgnoreCase("type")) {
					type = atts.getValue(i);
				}
			}
		}

		if (qName.equals("Iface")) {
			for (int i = 0; i < atts.getLength(); i++) {
				if (atts.getLocalName(i).equalsIgnoreCase("name")) {
					interfaceName = atts.getValue(i);
				}
			}
		}
		if (qName.equals("Addr")) {
			for (int i = 0; i < atts.getLength(); i++) {
				String addr = null;
				String mask = null;
				// Modified to read both ipv4 and ipv6 values as the same
				// attribute
				if (atts.getLocalName(i).equals("ipv6") || atts.getLocalName(i).equals("ipv4")) {
					addr = atts.getValue(i);
					mask = readMaskValue(atts);
					addrMaskMap.put(addr, mask);
				} else {
					key = atts.getLocalName(i);
					charValue = atts.getValue(i);
					measData.put(key, charValue);
				}
			}
		}
	}

	public void endElement(String uri, String name, String qName) throws SAXException {
		try {
			if (qName.equals("Iface")) {
				measData.put("IFNAME", interfaceName);
				if (measFile == null) {
					measFile = Main.createMeasurementFile(sourceFile, tagID, techPack, setType, setName,
							this.workerName, this.log);
					log.finest("Creating new file with worker:" + this.workerName);
				}
				if (addrMaskMap.isEmpty()) {
					log.fine("IPv6/IPv4 tag not present for interface:" + interfaceName);
					/*
					 * Write the values directly if IPv6 is not present for the
					 * interface
					 */
					writeToMeasFile();
				} else {
					for (String address : addrMaskMap.keySet()) {
						measData.put("addr", address);
						measData.put("mask", addrMaskMap.get(address));
						writeToMeasFile();
					}
					addrMaskMap.clear();
				}
				measData.clear();
			} else if (qName.equals("Response")) {
				measFile.close();
				measFile = null;
			}
		} catch (Exception e) {
			log.warning("Exception while closing the file" + e.getMessage());
		}
	}

	/**
	 * Extracts a substring from given string based on given regExp
	 * 
	 */
	public String parseFileName(String str, String regExp) {

		Pattern pattern = Pattern.compile(regExp);
		Matcher matcher = pattern.matcher(str);

		if (matcher.matches()) {
			String result = matcher.group(3);
			log.finest(" regExp (" + regExp + ") found from " + str + "  :" + result);
			return result;
		} else {
			log.warning("String " + str + " doesn't match defined regExp " + regExp);
		}

		return "";

	}

	public void writeToMeasFile() {
		try {
			measFile.addData("Filename", sourceFile.getName());
			measFile.addData("name", nodeName);
			measFile.addData("type", type);
			measFile.addData("DIRNAME", sourceFile.getDir());
			measFile.addData(measData);
			measFile.saveData();
		} catch (Exception e) {
			log.warning("Exception while saving data in parser" + e.getMessage());
		}
	}

	/**
	 * Returns the value of the MASK for any given attribute
	 * 
	 */
	
	private String readMaskValue(Attributes atts) {
		String mask = null;
		int i = 0;
		while (i < atts.getLength()) {
			if (atts.getLocalName(i).equalsIgnoreCase("mask")) {
				mask = atts.getValue(i);
				break;
			} else {
				i++;
				continue;
			}
		}
		return mask;
	}

}
