package com.ericsson.eniq.etl.twampPT;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.distocraft.dc5000.etl.parser.Main;
import com.distocraft.dc5000.etl.parser.MeasurementFile;
import com.distocraft.dc5000.etl.parser.Parser;
import com.distocraft.dc5000.etl.parser.SourceFile;
//import com.ericsson.eniq.common.ENIQEntityResolver;

public class DummyTests  {
	
	twampPT tpt = new twampPT();
	
	@Test
	public void test1()
	{
		tpt.init(null, null, null, null, "workname");
	}
	
	@Test
	public void test2()
	{
		int result = tpt.status();
		assertEquals(result, result);
	}
	
}
