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

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;

/**
 * Test that unbinding from an auto-delete exchange causes the exchange to go
 * away
 */
public class UnbindAutoDeleteExchange extends BrokerTestCase {
    public void testUnbind() throws IOException, InterruptedException {
        String exchange = "myexchange";
        channel.exchangeDeclare(exchange, "fanout", false, true, null);
        String queue = channel.queueDeclare().getQueue();
        channel.queueBind(queue, exchange, "");
        channel.queueUnbind(queue, exchange, "");

        try {
            channel.exchangeDeclarePassive(exchange);
            fail("exchange should no longer be there");
        }
        catch (IOException e) {
            checkShutdownSignal(AMQP.NOT_FOUND, e);
        }
    }
}
