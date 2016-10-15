// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import com.rabbitmq.client.Address;
import java.util.Arrays;

import com.rabbitmq.client.impl.ConnectionParams;
import com.rabbitmq.client.TopologyRecoveryException;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.impl.Frame;
import com.rabbitmq.client.impl.FrameHandler;

/**
 * Test suite for AMQConnection.
 */

public class AMQConnectionTest extends TestCase {
    // private static final String CLOSE_MESSAGE = "terminated by test";

    /**
     * Build a suite of tests
     * @return the test suite for this class
     */
    public static TestSuite suite() {
        TestSuite suite = new TestSuite("connection");
        suite.addTestSuite(AMQConnectionTest.class);
        return suite;
    }

    /** The mock frame handler used to test connection behaviour. */
    private MockFrameHandler _mockFrameHandler;
    private ConnectionFactory factory;
    private MyExceptionHandler exceptionHandler;

    /** Setup the environment for this test
     * @see junit.framework.TestCase#setUp()
     * @throws Exception if anything goes wrong
     */
    @Override protected void setUp() throws Exception {
        super.setUp();
        _mockFrameHandler = new MockFrameHandler();
        factory = new ConnectionFactory();
        exceptionHandler = new MyExceptionHandler();
        factory.setExceptionHandler(exceptionHandler);
    }

    /** Tear down the environment for this test
     * @see junit.framework.TestCase#tearDown()
     * @throws Exception if anything goes wrong
     */
    @Override protected void tearDown() throws Exception {
        factory = null;
        _mockFrameHandler = null;
        super.tearDown();
    }

    public void testNegativeTCPConnectionTimeout() {
        ConnectionFactory cf = new ConnectionFactory();
        try {
            cf.setConnectionTimeout(-10);
            fail("expected an exception");
        } catch (IllegalArgumentException _ignored) {
            // expected
        }
    }

    public void testNegativeProtocolHandshakeTimeout() {
        ConnectionFactory cf = new ConnectionFactory();
        try {
            cf.setHandshakeTimeout(-10);
            fail("expected an exception");
        } catch (IllegalArgumentException _ignored) {
            // expected
        }
    }

    public void testTCPConnectionTimeoutGreaterThanHandShakeTimeout() {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setHandshakeTimeout(3000);
        cf.setConnectionTimeout(5000);
    }

    public void testProtocolHandshakeTimeoutGreaterThanTCPConnectionTimeout() {
        ConnectionFactory cf = new ConnectionFactory();

        cf.setConnectionTimeout(5000);
        cf.setHandshakeTimeout(7000);

        cf.setConnectionTimeout(0);
        cf.setHandshakeTimeout(7000);
    }

    /** Check the AMQConnection does send exactly 1 initial header, and deal correctly with
     * the frame handler throwing an exception when we try to read data
     */
    public void testConnectionSendsSingleHeaderAndTimesOut() throws TimeoutException {
        IOException exception = new SocketTimeoutException();
        _mockFrameHandler.setExceptionOnReadingFrames(exception);
        assertEquals(0, _mockFrameHandler.countHeadersSent());
        try {
            ConnectionParams params = factory.params(Executors.newFixedThreadPool(1));
            new AMQConnection(params, _mockFrameHandler).start();
            fail("Connection should have thrown exception");
        } catch(IOException signal) {
           // As expected
        }
        assertEquals(1, _mockFrameHandler.countHeadersSent());
        // _connection.close(0, CLOSE_MESSAGE);
        List<Throwable> exceptionList = exceptionHandler.getHandledExceptions();
        assertEquals(Collections.<Throwable>singletonList(exception), exceptionList);
    }

    /** Check we can open a connection once, but not twice.
     * @throws IOException */
//    public void testCanOpenConnectionOnceOnly() throws IOException {
//        AMQConnection connection = new AMQConnection(_mockFrameHandler);
//        connection.open();
//        try {
//            connection.open();
//            fail("We shouldn't have been able to open this connection more than once.");
//        } catch(IOException ex) {
//            // as expected
//        }
//    }

    /**
     * Test that we catch timeout between connect and negotiation of the connection being finished.
     */
    public void testConnectionHangInNegotiation() {
        this._mockFrameHandler.setTimeoutCount(10); // to limit hang
        assertEquals(0, this._mockFrameHandler.countHeadersSent());
        try {
            ConnectionParams params = factory.params(Executors.newFixedThreadPool(1));
            new AMQConnection(params, this._mockFrameHandler).start();
            fail("Connection should have thrown exception");
        } catch(IOException signal) {
            // expected
        } catch(TimeoutException te) {
            // also fine: continuation timed out first
        }
        assertEquals(1, this._mockFrameHandler.countHeadersSent());
        List<Throwable> exceptionList = exceptionHandler.getHandledExceptions();
        assertEquals("Only one exception expected", 1, exceptionList.size());
        assertEquals("Wrong type of exception returned.", SocketTimeoutException.class, exceptionList.get(0).getClass());
    }

    public void testClientProvidedConnectionName() throws IOException, TimeoutException {
        String providedName = "event consumers connection";
        Connection connection = factory.newConnection(providedName);
        assertEquals(providedName, connection.getClientProvidedName());
        connection.close();

        List<Address> addrs1 = Arrays.asList(new Address("127.0.0.1"), new Address("127.0.0.1", 5672));
        connection = factory.newConnection(addrs1, providedName);
        assertEquals(providedName, connection.getClientProvidedName());
        connection.close();

        Address[] addrs2 = {new Address("127.0.0.1"), new Address("127.0.0.1", 5672)};
        connection = factory.newConnection(addrs2, providedName);
        assertEquals(providedName, connection.getClientProvidedName());
        connection.close();

        ExecutorService xs = Executors.newSingleThreadExecutor();
        connection = factory.newConnection(xs, providedName);
        assertEquals(providedName, connection.getClientProvidedName());
        connection.close();

        connection = factory.newConnection(xs, addrs1, providedName);
        assertEquals(providedName, connection.getClientProvidedName());
        connection.close();

        connection = factory.newConnection(xs, addrs2, providedName);
        assertEquals(providedName, connection.getClientProvidedName());
        connection.close();
    }

    /** Mock frame handler to facilitate testing. */
    private static class MockFrameHandler implements FrameHandler {
        /** How many times has sendHeader() been called? */
        private int _numHeadersSent;

        private int timeout;

        /** An optional exception for us to throw on reading frames */
        private IOException _exceptionOnReadingFrames;

        private int timeoutCount = 0;

        /** count how many headers we've sent
         * @return the number of sent headers
         */
        public int countHeadersSent() {
            return _numHeadersSent;
        }

        public void setExceptionOnReadingFrames(IOException exception) {
            _exceptionOnReadingFrames = exception;
        }

        public void setTimeoutCount(int timeoutCount) {
            this.timeoutCount = timeoutCount;
        }

        public Frame readFrame() throws IOException {
            if (_exceptionOnReadingFrames != null) {
                throw _exceptionOnReadingFrames;
            }
            if (this.timeoutCount > 0) {
                if (--this.timeoutCount == 0)
                    throw new IOException("Mock Framehandler: too many timeouts.");
            }
            return null; // simulate a socket timeout
        }

        public void sendHeader() throws IOException {
            _numHeadersSent++;
        }

        public void setTimeout(int timeoutMs) throws SocketException {
            this.timeout = timeoutMs;
        }

        public void writeFrame(Frame frame) throws IOException {
            // no need to implement this: don't bother writing the frame
        }

        public void close() {
            // nothing to do
        }

        public int getTimeout() throws SocketException {
            return this.timeout;
        }

        public InetAddress getAddress() {
            return null;
        }

        public int getPort() {
            return -1;
        }

        public void flush() throws IOException {
            // no need to implement this: don't bother writing the frame
        }

        public InetAddress getLocalAddress() {
            return null;
        }

        public int getLocalPort() {
            return -1;
        }
    }

    /** Exception handler to facilitate testing. */
    private class MyExceptionHandler implements ExceptionHandler {
        private final List<Throwable> _handledExceptions = new ArrayList<Throwable>();

        public void handleUnexpectedConnectionDriverException(Connection conn, Throwable ex) {
            _handledExceptions.add(ex);
        }

        public void handleReturnListenerException(Channel ch, Throwable ex) {
            fail("handleReturnListenerException: " + ex);
        }

        public void handleFlowListenerException(Channel ch, Throwable ex) {
            fail("handleFlowListenerException: " + ex);
        }

        public void handleConfirmListenerException(Channel ch, Throwable ex) {
            fail("handleConfirmListenerException: " + ex);
        }

        public void handleBlockedListenerException(Connection conn, Throwable ex) {
            fail("handleBlockedListenerException: " + ex);
        }

        public void handleConsumerException(Channel ch,
                                            Throwable ex,
                                            Consumer c,
                                            String consumerTag,
                                            String methodName)
        {
            fail("handleConsumerException " + consumerTag + " " + methodName + ": " + ex);
        }

        public void handleConnectionRecoveryException(Connection conn, Throwable ex) {
            _handledExceptions.add(ex);
        }

        public void handleChannelRecoveryException(Channel ch, Throwable ex) {
            _handledExceptions.add(ex);
        }

        public void handleTopologyRecoveryException(Connection conn, Channel ch, TopologyRecoveryException ex) {
            _handledExceptions.add(ex);
        }

        public List<Throwable> getHandledExceptions() {
            return _handledExceptions;
        }
    }
}
