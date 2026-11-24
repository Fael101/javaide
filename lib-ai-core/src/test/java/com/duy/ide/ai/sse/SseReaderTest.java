package com.duy.ide.ai.sse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SseReaderTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static List<String> readAll(String text) throws IOException {
        SseReader reader = new SseReader(new ByteArrayInputStream(text.getBytes(UTF_8)));
        List<String> events = new ArrayList<String>();
        String event;
        while ((event = reader.next()) != null) {
            events.add(event);
        }
        reader.close();
        return events;
    }

    @Test
    public void readsTwoEvents() throws Exception {
        List<String> events = readAll("data: a\n\ndata: b\n\n");
        assertEquals(2, events.size());
        assertEquals("a", events.get(0));
        assertEquals("b", events.get(1));
    }

    @Test
    public void ignoresCommentLines() throws Exception {
        List<String> events = readAll(": OPENROUTER PROCESSING\n\ndata: hello\n\n");
        assertEquals(1, events.size());
        assertEquals("hello", events.get(0));
    }

    @Test
    public void joinsMultilineDataWithNewline() throws Exception {
        List<String> events = readAll("data: first\ndata: second\n\n");
        assertEquals(1, events.size());
        assertEquals("first\nsecond", events.get(0));
    }

    @Test
    public void handlesCarriageReturns() throws Exception {
        List<String> events = readAll("data: one\r\n\r\ndata: two\r\n\r\n");
        assertEquals(2, events.size());
        assertEquals("one", events.get(0));
        assertEquals("two", events.get(1));
    }

    @Test
    public void returnsNullAfterStreamEnds() throws Exception {
        SseReader reader = new SseReader(new ByteArrayInputStream("data: x\n\n".getBytes(UTF_8)));
        assertEquals("x", reader.next());
        assertNull(reader.next());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void acceptsFinalEventWithoutBlankLine() throws Exception {
        List<String> events = readAll("data: last");
        assertEquals(1, events.size());
        assertEquals("last", events.get(0));
    }

    @Test
    public void emptyDataFieldYieldsEmptyString() throws Exception {
        List<String> events = readAll("data:\n\n");
        assertEquals(1, events.size());
        assertEquals("", events.get(0));
    }

    @Test
    public void ignoresUnknownFields() throws Exception {
        List<String> events = readAll("event: message\nid: 7\nretry: 100\ndata: payload\n\n");
        assertEquals(1, events.size());
        assertEquals("payload", events.get(0));
    }

    @Test
    public void survivesOneByteAtATimeDelivery() throws Exception {
        final byte[] bytes = "data: split\n\ndata: again\n\n".getBytes(UTF_8);
        InputStream trickle = new InputStream() {
            private int position;

            @Override
            public int read() {
                return position < bytes.length ? bytes[position++] & 0xff : -1;
            }
        };
        SseReader reader = new SseReader(trickle);
        assertEquals("split", reader.next());
        assertEquals("again", reader.next());
        assertNull(reader.next());
        reader.close();
    }
}
