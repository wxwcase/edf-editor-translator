package translator.logic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.xerces.dom.DocumentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Uses for testing BasicTranslation
 * @author wei wang, 2014-8-21
 */
public class Embla2EDFbrowserTranslatorFactory extends AbstractTranslatorFactory {
//public class EmblaAnnotationTranslator extends BasicTranslation implements AnnotationTranslator {

	private Document xmlRoot; // = new DocumentImpl(); // xml root
	private Element scoredEvents; // parent element of <Event>
	
	/**
	 * Default constructor
	 */
	public Embla2EDFbrowserTranslatorFactory() {
		super();
		System.out.println("=================================================================");
		System.out.println("Embla Factory:"); // test
		softwareVersion = "Embla";
		xmlRoot = new DocumentImpl(); // xml root
	}
	
	@Override
	public boolean read(String edfFile, String annotationFile, String mappingFile) {
		boolean result = false;		
		this.edfFile = edfFile;
		this.xmlAnnotation = annotationFile;
		map = readMapFile(mappingFile);	
		initLocalVariables(edfFile);
		document = resolveBOM(xmlAnnotation);
		result = recordEvents(document);		
		if(!result) {
			log("Cannot parse the events in the annotation file");
		}		
		return result;
	}

	/**
	 * Translates Embla annotation file using the mapping file and the corresponding EDF file
	 * @return true if successful
	 */
	@Override
	public boolean translate() {
		boolean result = false;
		Element root = xmlRoot.createElement("annotationlist");
		
		NodeList nodeList = document.getElementsByTagName("Event");
		System.out.println("   [Event size: " + nodeList.getLength() + "]"); // test
		for(int index = 0; index < nodeList.getLength(); index++) {
			Element parsedElement = null;
			Node node = nodeList.item(index);  // for each <event> node
			Element elem = (Element)node;
			parsedElement = parseEmblaXmlEvent(elem);
			if(parsedElement == null) {
				log("Can't parse event: " + index + " " + getElementByChildTag(elem, "Type"));
			}
			root.appendChild(parsedElement);
		}
		
//		root.appendChild(scoredEvents);
		xmlRoot.appendChild(root);
		result = true;
		System.out.println("   [Translation done]");  // test: should be moved out of this method
		return result;
	}
	
	/**
	 * Serializes XML to output file
	 * @param output the xml output file
	 * @return true if the process succeed
	 */
	@Override
	public boolean write2xml(String outputFile) {
		System.out.println("   >>> Inside EmblaTranslatorFactory write"); // test
		output = outputFile;
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes"); 
            DOMSource source = new DOMSource(xmlRoot);
            StreamResult file = new StreamResult(new File(output));
            transformer.transform(source, file);
            // System.out.println("\nXML DOM Created Successfully..");
            log("XML DOM Created Successfully..");
            System.out.println("   [Write done]"); // test
            System.out.println("=================================================================");// test
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            log("Error in writing out file");
            return false;
        }        
	}
	
	/**
	 * TODO
	 * Parses event element and returns an parsed element
	 * @param scoredEventElement the event name in String
	 * @return the parsed element
	 */
	private Element parseEmblaXmlEvent(Element scoredEventElement) {
		// only DESAT type has more values to be processed, others are the same
		Element scoredEvent = null;
		String eventType = getElementByChildTag(scoredEventElement, "Type");
		// map[1] contains keySet with event name
		if(map[1].keySet().contains(eventType)) {
			scoredEvent = parseEventElement(scoredEventElement);
		} else {						
			// no mapping event name found
			scoredEvent = xmlRoot.createElement("annotation");
			Element eventConcept = xmlRoot.createElement("description");
			Element startElement = xmlRoot.createElement("onset");
			Element durationElement = xmlRoot.createElement("duration");
			Element notesElement = xmlRoot.createElement("Notes");
				
			eventConcept.appendChild(xmlRoot.createTextNode("Technician Notes"));
			notesElement.appendChild(xmlRoot.createTextNode(eventType));
			
			String startTime = getElementByChildTag(scoredEventElement, "StartTime");
			String stopTime = getElementByChildTag(scoredEventElement, "StopTime");
			String durationTime = getDurationInSeconds(startTime, stopTime);
			startElement.appendChild(xmlRoot.createTextNode(startTime)); // newly added 2-14-8-28
			durationElement.appendChild(xmlRoot.createTextNode(durationTime));
					
			scoredEvent.appendChild(startElement);
			scoredEvent.appendChild(durationElement);
			scoredEvent.appendChild(eventConcept);
			scoredEvent.appendChild(notesElement);
		}		

		return scoredEvent;
	}

	////////////////////////////////////////////////////
	////// Private utility methods start          //////
	////////////////////////////////////////////////////
	
	/**
	 * Get Event with tag of eventConcept, start and duration
	 * @param scoredEventElement
	 * @return
	 */
	private List<Element> getLocation(Element scoredEventElement) {
		// {eventConcept, duration, start}
		List<Element> list = new ArrayList<Element>();
		String eventType = getElementByChildTag(scoredEventElement, "Type");
		String annLocation = getElementByChildTag(scoredEventElement, "Location");
//		Element eventCategory = xmlRoot.createElement("EventType");
		Element eventConcept = xmlRoot.createElement("description");		
		Element duration = xmlRoot.createElement("duration");
		Element start = xmlRoot.createElement("onset");
//		Element input = xmlRoot.createElement("SignalLocation");
//		List<String> signalLocation = (List<String>)map[3].get(eventType);
//		String signalLocation = getSignalLocationFromEvent(scoredEventElement, annLocation);
		
//		Node inputNode = xmlRoot.createTextNode(signalLocation);
//		input.appendChild(inputNode);
//		Node nameNode = xmlRoot.createTextNode(eventType); // bug fixed: wei wang, 2014-8-26
		//		Node nameNode = xmlRoot.createTextNode((String) ((ArrayList<String>) map[1].get(eventType)).get(1));
//		Node nameNode = xmlRoot.createTextNode((String) (map[1].get(eventType)));
		Node nameNode = xmlRoot.createTextNode(eventType);
//		String category = map[2].get(eventType) == null ? "" : (String) map[2].get(eventType);
//		Node categoryNode = xmlRoot.createTextNode(category);
//		eventCategory.appendChild(categoryNode);
		eventConcept.appendChild(nameNode);
		String startTime = getElementByChildTag(scoredEventElement, "StartTime");
		String relativeStart = getEventStartTime(timeStart[0], startTime);
		String stopTime = getElementByChildTag(scoredEventElement, "StopTime");
		String durationTime = getDurationInSeconds(startTime, stopTime);
		start.appendChild(xmlRoot.createTextNode(startTime));
		duration.appendChild(xmlRoot.createTextNode(durationTime));
		
//		list.add(eventCategory);
		list.add(start);
		list.add(duration);
		list.add(eventConcept);		
//		list.add(input);
		
		return list;
	}
	
	/* (non-Javadoc)
	 * @see translator.logic.AbstractTranslatorFactory#getSignalLocationFromEvent(org.w3c.dom.Element, java.lang.String)
	 */
	public String getSignalLocationFromEvent(Element scoredEvent, String annLocation) {
	  // e.g. annLocation = "SpO2.Averaged-Probe" or "Resp.Flow-Cannula.Nasal"
	  // idea: split annLocation to String[] such as {"SpO2", "Averaged", "Probe"}
	  //       check defaultEdfSignals with each one of String[]
	  String result = signalLabels[0]; // initialize to the first EDF signal

    String eventName = getElementByChildTag(scoredEvent, "Type");
    String[] tokens = annLocation.split("[.-]");
    @SuppressWarnings("unchecked")
    List<String> defaultSignals = (List<String>)map[3].get(eventName);
    List<String> edfSignals = Arrays.asList(signalLabels);
    
    for (String token : tokens) {
      if (edfSignals.contains(token)) {
        result = token;
        return result;
      }
    }
    
    for (String signal : defaultSignals) {
      if (edfSignals.contains(signal)) {
        result = signal;
        return result;
      }
    }

    return result;
  }
	
	private List<Element> getUserVariables(Element scoredEventElement) {
		List<Element> list = new ArrayList<Element>();
		String eventType = getElementByChildTag(scoredEventElement, "Type");
		if(eventType.equals("DESAT")) {
			Element spO2Nadir = xmlRoot.createElement("SpO2Nadir");
			Element spO2Baseline = xmlRoot.createElement("SpO2Baseline");
			String desatStartVal = getUserVariableValue(scoredEventElement, "Begin of desat");
			String desatEndVal = getUserVariableValue(scoredEventElement, "End of desat");
			spO2Nadir.appendChild(xmlRoot.createTextNode(desatEndVal));
			spO2Baseline.appendChild(xmlRoot.createTextNode(desatStartVal));
			list.add(spO2Nadir);
			list.add(spO2Baseline);			
		}
		return list;
	}
	
	private Element parseEventElement(Element scoredEventElement) {
		List<Element> locationList = getLocation(scoredEventElement);
//		List<Element> userVariableList = getUserVariables(scoredEventElement);
		Element scoredEvent = null;
		if(xmlRoot != null) {
			scoredEvent = xmlRoot.createElementNS(null, "annotation");
		} else {
			log("ERROR: root document is null");
			return null;
		}
		for(Element element : locationList)
			scoredEvent.appendChild(element);
//		for(Element element : userVariableList)
//			scoredEvent.appendChild(element);
		return scoredEvent;
	}
	
	/**
	 * Gets the value of the specified key from UserVariable parameter of this event
	 * @param scoredEventElement the scored event
	 * @param paramKey the key corresponding to the value needed
	 * @return the value corresponding to the key
	 */
	private String getUserVariableValue(Element scoredEventElement, String paramKey) {
		NodeList rootParamsList = scoredEventElement.getElementsByTagName("Parameters");
		Element rootParams = (Element)rootParamsList.item(0);		
		NodeList rootParamList = rootParams.getElementsByTagName("Parameter");
		Element userVarElement = null;
		for(int i = 0; i < rootParamList.getLength(); i++) {
			Element userValElement = (Element)rootParamList.item(i);
			NodeList keys = userValElement.getElementsByTagName("Key");
			Element firstKeyElement = (Element)keys.item(0);
			String keyvalue = getText(firstKeyElement);
			if("UserVariables".equals(keyvalue)) {
				userVarElement = userValElement;
			}
		}
		NodeList values = userVarElement.getElementsByTagName("Value");
		Element value = (Element)values.item(0);
		NodeList paramsList = value.getElementsByTagName("Parameters");
		Element parameters = (Element)paramsList.item(0);
		NodeList finalParamList = parameters.getElementsByTagName("Parameter");

		String resultValue = "";
		for(int index = 0; index < finalParamList.getLength(); index++) {
			Element parent = (Element)finalParamList.item(index);
			String keyVal = getElementByChildTag(parent, "Key");
			if(keyVal.equals(paramKey)) {
				resultValue = getElementByChildTag(parent, "Value");
			}
		}
		return resultValue;
	}
	
	/**
	 * Gets the start time of an event, relative to the EDF recording time
	 * @param edfClockStart the start time recorded in the EDF file, of format "00.00.00 00.00.00"(start date, start time)
	 * @param eventStart the start time of an event
	 * @return start time of format "xxxxx.x"
	 */
	public String getEventStartTime(String edfClockStart, String eventStart) {
//		System.out.println("EDF:" + edfClockStart + "|" + "EVENT:" + eventStart);
		String edftime = edfClockStart.substring(9, 17); // 00.00.00
//		System.out.println("EDF time: " + edftime);
		String eventTime = eventStart.substring(11, 19); // 00:00:00 hh:mm:ss
//		System.out.println("EventTime: " + eventTime);
		String edfDate = edfClockStart.substring(0, 8);
//		System.out.println("EDF Date: " + edfDate);
		String eventDate = eventStart.substring(0, 10);
//		System.out.println("Event Date: " + eventDate);

			
		SimpleDateFormat edfTF = new SimpleDateFormat("hh.mm.ss");
		SimpleDateFormat eventFormat = new SimpleDateFormat("HH:mm:ss");
			
		SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat formatEdfDate = new SimpleDateFormat("dd.mm.yy");
		Calendar c = Calendar.getInstance();
		String finalDate = "";
		try {
			Date edf1 = edfTF.parse(edftime);
			Date event1 = eventFormat.parse(eventTime);
			if(edf1.after(event1)) {
				// edf date = event date - 1;
				c.setTime(formatDate.parse(eventDate));
				c.add(Calendar.DATE, -1);  // number of days to add
				String fdate = formatDate.format(c.getTime());
				finalDate = fdate.substring(8, 10) + "." + fdate.substring(5, 7) + "." + fdate.substring(2, 4);
			} else {
				finalDate = eventDate.substring(8, 10) + "." + eventDate.substring(5, 7) + "." + eventDate.substring(2, 4);
			}
		} catch (ParseException e) {
			log("Cannot parse start date.");
		}		
		edfClockStart = finalDate + " " + edfClockStart.substring(9);
		String format1 = "dd.MM.yy hh.mm.ss";
		String format2 = "yyyy-MM-dd'T'HH:mm:ss";
		String time;
		SimpleDateFormat sdf1 = new SimpleDateFormat(format1, Locale.US);
		SimpleDateFormat sdf2 = new SimpleDateFormat(format2, Locale.US);
		try {
			Date edfd = sdf1.parse(edfClockStart);
			Date eventd = sdf2.parse(eventStart);
//			System.out.println("EDF/EVENT:\n" + edfd.toString() + "\n" + eventd);
//			System.out.println("!!!!!!!!!" + edfd.before(eventd));
			long diff = eventd.getTime() - edfd.getTime();  // milliseconds
//			System.out.println("edfd: " + edfd.getTime());
//			System.out.println("eventd: " + eventd.getTime());
//			System.out.println("diff: " + diff);
			long timeStart = diff / 1000;			
			String start_suf = eventStart.substring(20, 21);
			time = String.valueOf(timeStart);
			time += "." + start_suf;
			return time;
		} catch (ParseException e) {
			e.printStackTrace();
			log("Cannot parse start time\n");
			return "";
		}
	}
	
	/**
	 * Gets the string representation of the duration
	 * @param start the start time
	 * @param end the end time
	 * @return duration
	 */
	private String getDurationInSeconds(String start, String end) {
		// SimpleDataFormat did not handle microseconds well, so I wrote the code to handle it
		// by wei wang, 2014-8-9
		String format = "yyyy-MM-dd'T'HH:mm:ss";
		String result = "";
		SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
		try {
			Date startDate = sdf.parse(start);
			Date endDate = sdf.parse(end);
			long diff = endDate.getTime() - startDate.getTime(); // in milliseconds
			long duration = diff / 1000;			
			
//			2010-01-26T22:43:30.123000 sample			
			String start_suf = start.substring(20); // 123000 in 2010-01-26T22:43:30.123000, suffix  
			String end_suf = end.substring(20);
			int s = Integer.valueOf(start_suf);
			int e = Integer.valueOf(end_suf);
			int res = 0;
			String finalDuration = "";
			if(e < s) {
				res = e + 1000000 - s;
				duration -= 1;				
			} else {
				res = e - s;
			}
			long finalRes = Math.round(res * 1.0 / 100000);
			finalDuration = duration + "." + String.valueOf(finalRes);
			result = String.valueOf(finalDuration);
		} catch (ParseException e) {
			e.printStackTrace();
			log("Cannot parse duration");
		}
		return result;
	}
	
	/**
	 * Parses the Embla xml annotation file and generates the event names
	 * @param emblaXmlFile the Embla annotation file
	 * @return true if the process is successful
	 */
	private boolean recordEvents(Document doc) {
		String eventName;
		Set<String> eventNames = new HashSet<String>();
		NodeList nodeList = doc.getElementsByTagName("EventType");
		if(nodeList == null) {
			log("Cannot find EventType");
			return false;
		}
		for(int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if(node.hasChildNodes()) {
				Node last = node.getLastChild();
				eventName = last.getNodeValue();
				eventNames.add(eventName);
			}
		}
		events = eventNames;
		return true;
	}
	
	/**
	 * Appends elements of string format to the ScoredEvent element
	 * @param doc the Document to which the elements to be added
	 * @param elements elements to be added to the ScoredEvent element
	 * @return the ScoredEvent element
	 */
	private Element addElements(Document doc, String[] elements) {
		Element eventElement = doc.createElement("annotation");
	
		Element startElement = doc.createElement("onset");
		startElement.appendChild(doc.createTextNode(elements[1]));
		eventElement.appendChild(startElement);
		Element durationElement = doc.createElement("duration");
		durationElement.appendChild(doc.createTextNode(elements[2]));
		
		Element nameElement = doc.createElement("description");
    nameElement.appendChild(doc.createTextNode(elements[0]));
    eventElement.appendChild(nameElement);
		eventElement.appendChild(durationElement);
		return eventElement;
	}

	///////////////////////////////////
	//// Private utility methods end //
	//// Getters and Setters START   //
	///////////////////////////////////
	
	public String getSoftwareVersion() {
		return softwareVersion;
	}

	public void setSoftwareVersion(String softwareVersion) {
		this.softwareVersion = softwareVersion;
	}

	public String getXmlAnnotation() {
		return xmlAnnotation;
	}

	public void setXmlAnnotation(String xmlAnnotation) {
		this.xmlAnnotation = xmlAnnotation;
	}

	public String getEdfFile() {
		return edfFile;
	}

	public void setEdfFile(String edfFile) {
		this.edfFile = edfFile;
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

	public HashMap<String, Object>[] getMap() {
		return map;
	}

	public void setMap(HashMap<String, Object>[] map) {
		this.map = map;
	}

	public Document getXmlRoot() {
		return xmlRoot;
	}

	public Element getScoredEvents() {
		return scoredEvents;
	}

	public String[] getTimeStart() {
		return timeStart;
	}

	public Document getDocument() {
		return document;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	public Set<String> getEvents() {
		return events;
	}

	public void setEvents(Set<String> events) {
		this.events = events;
	}

	@Override
	public boolean write2JSON(String outputFile) {
		boolean result = false;
		String resultFinal = FormatedWriter.xml2json(xmlRoot);
		try (PrintStream out = new PrintStream(new FileOutputStream(outputFile))) {
		    out.print(resultFinal);
		    result = true;
		} catch(Exception e) {
			// log result
		}
		return result;
	}
	
	/**
	 * Reads mapping file and saves as an array of HashMap of info (epoch, length), (eventName, translatedName), (eventName, category)
	 * Can be put in a higher hierarchy
	 * @param mapFile the mapping file name
	 * @return  the mapping in form of HashMap
	 */
	public HashMap<String,Object>[] readMapFile(String mapFile) {
		// System.out.println("Read map file...");  // for test
		@SuppressWarnings("unchecked")
		// HashMap[] map = new HashMap[3]; // original
		HashMap<String,Object>[] map = (HashMap<String,Object>[]) Array.newInstance(HashMap.class, 4);
		// HashMap map = new HashMap();
		try {
			BufferedReader input =  new BufferedReader(new FileReader(mapFile));
			try {
				String line = input.readLine();
				// <EpochLength, #num>
				HashMap<String,Object> epoch = new HashMap<String,Object>();
				// <Event, EventConcept>
				HashMap<String,Object> events = new HashMap<String,Object>();
				// <Event, EventType>
				HashMap<String,Object> types = new HashMap<String,Object>();
				// <Event, SignalLocation>
				HashMap<String,Object> signalLocation = new HashMap<String,Object>();
				while ((line = input.readLine()) != null) {
					String[] data = line.split(",");
					String eventCategoryInPipe = data[0];
					String eventNameInPipe = data[3].trim();
//					String eventTypeLowerCase = data[0].toLowerCase();
					List<String> defaultSignals = new ArrayList<>();
					if (data[0].compareTo("EpochLength") == 0) {
						epoch.put(data[0], data[2]);
					} else {
					  if (data[4].length() != 0) {
					    for (String sname : data[4].split("#")) {
					      defaultSignals.add(sname);
					    }
					  }
						events.put(data[3].trim(),eventNameInPipe);
						types.put(data[3].trim(), eventCategoryInPipe);
						signalLocation.put(data[3].trim(), defaultSignals);
					}
				}	
				// System.out.println(map[2].values().size());
				map[0] = epoch;
				map[1] = events;
				map[2] = types;
				map[3] = signalLocation;
			} finally {
				input.close();
			}
		} catch(IOException e) {
			e.printStackTrace();			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		return map;
	}
	
	//////////////////////////////////
	//// Getters and Setters END /////
	//////////////////////////////////

}
