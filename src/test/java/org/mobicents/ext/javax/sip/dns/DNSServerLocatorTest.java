/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.ext.javax.sip.dns;

import static org.junit.Assert.*;
import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.stack.HopImpl;

import java.text.ParseException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.sip.ListeningPoint;
import javax.sip.address.AddressFactory;
import javax.sip.address.Hop;
import javax.sip.address.SipURI;
import javax.sip.address.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xbill.DNS.DClass;
import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;

import static org.mockito.Mockito.*;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class DNSServerLocatorTest {
	AddressFactory addressFactory;
	DefaultDNSServerLocator dnsServerLocator;
	Set<String> supportedTransports;
	SipURI sipURI;
	String host = "iptel.org";
	public static final String LOCALHOST = "127.0.0.1";	
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		addressFactory = new AddressFactoryImpl();
		supportedTransports = new HashSet<String>();
		supportedTransports.add(ListeningPoint.UDP);
		supportedTransports.add(ListeningPoint.TCP);
		dnsServerLocator = new DefaultDNSServerLocator(supportedTransports);
		sipURI = addressFactory.createSipURI("jean",host);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link org.mobicents.ext.javax.sip.dns.DefaultDNSServerLocator#resolveHostByDnsSrvLookup(javax.sip.address.SipURI)}.
	 * @throws ParseException 
	 */
	@Test
	public void testGetDefaultTransportForSipUri() throws ParseException {
		sipURI.setPort(5080);
		assertEquals(ListeningPoint.UDP, dnsServerLocator.getDefaultTransportForSipUri(sipURI));
		sipURI.setSecure(true);
		assertEquals(ListeningPoint.TLS, dnsServerLocator.getDefaultTransportForSipUri(sipURI));
	}
	
	/**
	 * Test method for {@link org.mobicents.ext.javax.sip.dns.DefaultDNSServerLocator#locateHops(javax.sip.address.URI)}.
	 * @throws ParseException 
	 */
	@Test
	public void testRealExample() throws ParseException {
		Queue<Hop> hops = dnsServerLocator.locateHops(sipURI);
		assertNotNull(hops);
		assertTrue(hops.size() > 0);
	}
	
	@Test
	public void testResolveHostByAandAAAALookup() throws ParseException {
		String transport = ListeningPoint.UDP;
		int port = 5080;
		
		DNSLookupPerformer dnsLookupPerformer = mock(DefaultDNSLookupPerformer.class);
		dnsServerLocator.setDnsLookupPerformer(dnsLookupPerformer);
		LinkedList<Hop> mockedHops = new LinkedList<Hop>();
		mockedHops.add(new HopImpl(LOCALHOST, port, transport));
		when(dnsLookupPerformer.locateHopsForNonNumericAddressWithPort(host, port, transport.toLowerCase())).thenReturn(mockedHops);
		
		sipURI.setTransportParam(transport);
		sipURI.setPort(port);
		Queue<Hop> hops = dnsServerLocator.resolveHostByDnsSrvLookup(sipURI);
		assertNotNull(hops);
		assertEquals(1, hops.size());
		Hop hop = hops.poll();
		assertEquals(port, hop.getPort());
		assertEquals(transport, hop.getTransport());
		assertEquals(LOCALHOST, hop.getHost());
	}
	
	@Test
	public void testResolveHostByAandAAAALookupCheckEmpty() throws ParseException {
		String transport = ListeningPoint.UDP;
		int port = 5080;
		
		DNSLookupPerformer dnsLookupPerformer = mock(DefaultDNSLookupPerformer.class);
		dnsServerLocator.setDnsLookupPerformer(dnsLookupPerformer);
		LinkedList<Hop> mockedHops = new LinkedList<Hop>();
		when(dnsLookupPerformer.locateHopsForNonNumericAddressWithPort(host, port, transport.toLowerCase())).thenReturn(mockedHops);
		
		sipURI.setTransportParam(transport);
		sipURI.setPort(port);
		Queue<Hop> hops = dnsServerLocator.resolveHostByDnsSrvLookup(sipURI);
		assertNotNull(hops);
		assertEquals(0, hops.size());
	}
	
	@Test
	public void testResolveHostNoPortButTransportSpecified() throws ParseException, TextParseException {
		String transport = ListeningPoint.UDP;
		
		DNSLookupPerformer dnsLookupPerformer = mock(DefaultDNSLookupPerformer.class);
		dnsServerLocator.setDnsLookupPerformer(dnsLookupPerformer);
		LinkedList<Record> mockedHops = new LinkedList<Record>();
		// mocking the name because localhost is not absolute and localhost. cannot be resolved 
		Name name = mock(Name.class);
		when(name.isAbsolute()).thenReturn(true);
		when(name.toString()).thenReturn("localhost");
		mockedHops.add(new SRVRecord(new Name("_sip._" + transport.toLowerCase() + "." + host + "."), DClass.IN, 1000L, 0, 0, 5060, name));
		when(dnsLookupPerformer.performSRVLookup("_sip._" + transport.toLowerCase() + "." + host)).thenReturn(mockedHops);
		
		sipURI.setTransportParam(transport);
		Queue<Hop> hops = dnsServerLocator.resolveHostByDnsSrvLookup(sipURI);
		assertNotNull(hops);
		assertTrue(hops.size() > 0);
		Hop hop = hops.poll();
		assertEquals(5060, hop.getPort());
		assertEquals(transport.toLowerCase(), hop.getTransport());
		assertEquals(LOCALHOST, hop.getHost());
	}
	
	@Test
	public void testResolveHostNoPortButTransportSpecifiedNoSRVFound() throws ParseException, TextParseException {
		String transport = ListeningPoint.UDP;
		sipURI.setHost("localhost");
		
		DNSLookupPerformer dnsLookupPerformer = mock(DefaultDNSLookupPerformer.class);
		dnsServerLocator.setDnsLookupPerformer(dnsLookupPerformer);
		LinkedList<Hop> mockedHops = new LinkedList<Hop>();
		mockedHops.add(new HopImpl(LOCALHOST, 5060, transport));
		when(dnsLookupPerformer.locateHopsForNonNumericAddressWithPort("localhost", -1, transport.toLowerCase())).thenReturn(mockedHops);
		
		sipURI.setTransportParam(transport);
		Queue<Hop> hops = dnsServerLocator.resolveHostByDnsSrvLookup(sipURI);
		assertNotNull(hops);
		assertTrue(hops.size() > 0);
		Hop hop = hops.poll();
		assertEquals(5060, hop.getPort());
		assertEquals(transport, hop.getTransport());
		assertEquals(LOCALHOST, hop.getHost());
	}
	
	@Test
	public void testResolveHostNoPortNoTransportSpecifiedNAPTRAndSRVFound() throws ParseException, TextParseException {
		String transport = ListeningPoint.UDP;
		
		DNSLookupPerformer dnsLookupPerformer = mock(DefaultDNSLookupPerformer.class);
		dnsServerLocator.setDnsLookupPerformer(dnsLookupPerformer);
		
		List<NAPTRRecord> mockedNAPTRRecords = new LinkedList<NAPTRRecord>();
		// mocking the name because localhost is not absolute and localhost. cannot be resolved 
		Name name = mock(Name.class);
		when(name.isAbsolute()).thenReturn(true);
		when(name.toString()).thenReturn("localhost");
		mockedNAPTRRecords.add(new NAPTRRecord(new Name(host + "."), DClass.IN, 1000, 0, 0, "s", "SIP+D2U", "", new Name("_sip._" + transport.toLowerCase() + "." + host + ".")));		
		when(dnsLookupPerformer.performNAPTRLookup(host, false, supportedTransports)).thenReturn(mockedNAPTRRecords);
		List<Record> mockedSRVRecords = new LinkedList<Record>();
		mockedSRVRecords.add(new SRVRecord(new Name("_sip._" + transport.toLowerCase() + "." + host + "."), DClass.IN, 1000L, 0, 0, 5060, name));
		when(dnsLookupPerformer.performSRVLookup("_sip._" + transport.toLowerCase() + "." + host + ".")).thenReturn(mockedSRVRecords);
		
		Queue<Hop> hops = dnsServerLocator.resolveHostByDnsSrvLookup(sipURI);
		assertNotNull(hops);
		assertTrue(hops.size() > 0);
		Hop hop = hops.poll();
		assertEquals(5060, hop.getPort());
		assertEquals(transport.toLowerCase(), hop.getTransport());
		assertEquals(LOCALHOST, hop.getHost());
	}
	
	@Test
	public void testResolveENUM() throws ParseException, TextParseException {
		DNSLookupPerformer dnsLookupPerformer = mock(DefaultDNSLookupPerformer.class);
		dnsServerLocator.setDnsLookupPerformer(dnsLookupPerformer);
		
		List<NAPTRRecord> mockedNAPTRRecords = new LinkedList<NAPTRRecord>();
		// mocking the name because localhost is not absolute and localhost. cannot be resolved 
		Name name = mock(Name.class);
		when(name.isAbsolute()).thenReturn(true);
		when(name.toString()).thenReturn("!^.*$!sip:jean@localhost!.");
		mockedNAPTRRecords.add(new NAPTRRecord(new Name("7.6.5.4.3.2.1.5.5.5.8.5.3.e164.arpa" + "."), DClass.IN, 1000, 0, 0, "s", "E2U+sip", "", name));		
		when(dnsLookupPerformer.performNAPTRLookup("7.6.5.4.3.2.1.5.5.5.8.5.3.e164.arpa", false, supportedTransports)).thenReturn(mockedNAPTRRecords);
		
		URI telURI = addressFactory.createTelURL("+358-555-1234567");
		SipURI resolvedSipURI = dnsServerLocator.getSipURI(telURI);
		assertNotNull(resolvedSipURI);
		assertEquals("sip:jean@localhost", resolvedSipURI.toString());
	}

	@Test
	public void testResolveHostNoPortNoTransportSpecifiedNoNAPTRFound() throws ParseException, TextParseException {
		String transport = ListeningPoint.UDP;
		
		DNSLookupPerformer dnsLookupPerformer = mock(DefaultDNSLookupPerformer.class);
		dnsServerLocator.setDnsLookupPerformer(dnsLookupPerformer);
		
		// mocking the name because localhost is not absolute and localhost. cannot be resolved 
		Name name = mock(Name.class);
		when(name.isAbsolute()).thenReturn(true);
		when(name.toString()).thenReturn("localhost");
		List<Record> mockedSRVRecords = new LinkedList<Record>();
		mockedSRVRecords.add(new SRVRecord(new Name("_sip._" + transport.toLowerCase() + "." + host + "."), DClass.IN, 1000L, 0, 0, 5060, name));
		List<Record> mockedSRVRecordsTCP = new LinkedList<Record>();
		mockedSRVRecordsTCP.add(new SRVRecord(new Name("_sip._" + "tcp" + "." + host + "."), DClass.IN, 1000L, 0, 0, 5060, name));
		when(dnsLookupPerformer.performSRVLookup("_sip._" + transport.toLowerCase() + "." + host)).thenReturn(mockedSRVRecords);
		when(dnsLookupPerformer.performSRVLookup("_sip._" + "tcp" + "." + host)).thenReturn(mockedSRVRecordsTCP);
		
		Queue<Hop> hops = dnsServerLocator.resolveHostByDnsSrvLookup(sipURI);
		assertNotNull(hops);
		assertTrue(hops.size() > 0);
		Hop hop = hops.poll();
		assertEquals(5060, hop.getPort());
		assertEquals(transport.toLowerCase(), hop.getTransport());
		assertEquals(LOCALHOST, hop.getHost());
	}
	
	@Test
	public void testNAPTRComparator() throws TextParseException {
		List<NAPTRRecord> mockedNAPTRRecords = new LinkedList<NAPTRRecord>();
		// mocking the name because localhost is not absolute and localhost. cannot be resolved 
		Name name = mock(Name.class);
		when(name.isAbsolute()).thenReturn(true);
		when(name.toString()).thenReturn("localhost");
			
		mockedNAPTRRecords.add(new NAPTRRecord(new Name(host + "."), DClass.IN, 1000L, 90, 50, "s", "SIP+D2T", "", new Name("_sip._" + ListeningPoint.TCP.toLowerCase() + "." + host + ".")));	
		mockedNAPTRRecords.add(new NAPTRRecord(new Name(host + "."), DClass.IN, 1000L, 100, 50, "s", "SIP+D2U", "", new Name("_sip._" + ListeningPoint.UDP.toLowerCase() + "." + host + ".")));
		mockedNAPTRRecords.add(new NAPTRRecord(new Name(host + "."), DClass.IN, 1000L, 50, 50, "s", "SIPS+D2T", "", new Name("_sips._" + ListeningPoint.TLS.toLowerCase() + "." + host + ".")));
		
		// Sorting the records
		java.util.Collections.sort(mockedNAPTRRecords, new NAPTRRecordComparator());
		
		assertEquals("SIPS+D2T", mockedNAPTRRecords.get(0).getService());
		assertEquals("SIP+D2T", mockedNAPTRRecords.get(1).getService());
		assertEquals("SIP+D2U", mockedNAPTRRecords.get(2).getService());
	}
	
	@Test
	public void testSRVComparator() throws TextParseException {
		// mocking the name because localhost is not absolute and localhost. cannot be resolved 			
		List<SRVRecord> mockedSRVRecords = new LinkedList<SRVRecord>();
		mockedSRVRecords.add(new SRVRecord(new Name("_sip._" + ListeningPoint.TCP.toLowerCase() + "." + host + "."), DClass.IN, 1000L, 1, 9, 5060, new Name("old-slow-box.example.com.")));	
		mockedSRVRecords.add(new SRVRecord(new Name("_sip._" + ListeningPoint.TCP.toLowerCase() + "." + host + "."), DClass.IN, 1000L, 1, 10, 5060, new Name("old2-slow-box.example.com.")));
		mockedSRVRecords.add(new SRVRecord(new Name("_sip._" + ListeningPoint.TCP.toLowerCase() + "." + host + "."), DClass.IN, 1000L, 3, 9, 5060, new Name("new-fast2-box.example.com.")));
		mockedSRVRecords.add(new SRVRecord(new Name("_sip._" + ListeningPoint.TCP.toLowerCase() + "." + host + "."), DClass.IN, 1000L, 3, 9, 5060, new Name( "new-fast-box.example.com.")));		
				
		// Sorting the records
		java.util.Collections.sort(mockedSRVRecords, new SRVRecordComparator());
		
		assertEquals("old2-slow-box.example.com.", mockedSRVRecords.get(0).getTarget().toString());
		assertEquals("old-slow-box.example.com.", mockedSRVRecords.get(1).getTarget().toString());		
		assertEquals("new-fast-box.example.com.", mockedSRVRecords.get(2).getTarget().toString());
		assertEquals("new-fast2-box.example.com.", mockedSRVRecords.get(3).getTarget().toString());		
	}
}
