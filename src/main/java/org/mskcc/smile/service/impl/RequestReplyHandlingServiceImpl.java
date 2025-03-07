package org.mskcc.smile.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.cmo.messaging.Gateway;
import org.mskcc.cmo.messaging.MessageConsumer;
import org.mskcc.smile.model.SampleMetadata;
import org.mskcc.smile.service.CmoLabelGeneratorService;
import org.mskcc.smile.service.RequestReplyHandlingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 * @author DivyaMadala
 *
 */
@Service
public class RequestReplyHandlingServiceImpl implements RequestReplyHandlingService {

    @Value("${request_reply.cmo_label_generator_topic:}")
    private String CMO_LABEL_GENERATOR_REQREPLY_TOPIC;

    @Value("${request_reply.patient_samples_topic:}")
    private String PATIENT_SAMPLES_REQUEST_TOPIC;

    @Value("${request_reply.samples_by_alt_id_topic}")
    private String SAMPLES_BY_ALT_ID_REQREPLY_TOPIC;

    @Value("${num.new_request_handler_threads:1}")
    private int NUM_NEW_REQUEST_HANDLERS;

    @Autowired
    private CmoLabelGeneratorService cmoLabelGeneratorService;

    private final ObjectMapper mapper = new ObjectMapper();
    private static Gateway messagingGateway;
    private static final Log LOG = LogFactory.getLog(RequestReplyHandlingServiceImpl.class);
    private static boolean initialized = false;
    private static volatile boolean shutdownInitiated;

    private static final ExecutorService exec = Executors.newCachedThreadPool();
    private static final BlockingQueue<ReplyInfo> cmoLabelGeneratorReqReplyQueue =
            new LinkedBlockingQueue<ReplyInfo>();
    private static CountDownLatch cmoLabelGeneratorHandlerShutdownLatch;

    private class ReplyInfo {
        String requestMessage;
        String replyTo;

        ReplyInfo(String requestMessage, String replyTo) {
            this.requestMessage = requestMessage;
            this.replyTo = replyTo;
        }

        String getRequestMessage() {
            return requestMessage;
        }

        String getReplyTo() {
            return replyTo;
        }
    }

    private class CmoLabelGeneratorReqReplyHandler implements Runnable {

        final Phaser phaser;
        boolean interrupted = false;

        CmoLabelGeneratorReqReplyHandler(Phaser phaser) {
            this.phaser = phaser;
        }

        @Override
        public void run() {
            phaser.arrive();
            while (true) {
                try {
                    // reply info request message contains cmo patient id
                    ReplyInfo replyInfo = cmoLabelGeneratorReqReplyQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (replyInfo != null) {
                        SampleMetadata sample = mapper.readValue(replyInfo.getRequestMessage(),
                                SampleMetadata.class);
                        List<SampleMetadata> existingPatientSamples
                                = getExistingPatientSamples(sample.getCmoPatientId());
                        List<SampleMetadata> samplesByAltId
                                = getSamplesByAltId(sample.getAdditionalProperty("altId"));
                        String updatedCmoSampleLabel =
                                cmoLabelGeneratorService.generateCmoSampleLabel(sample,
                                        existingPatientSamples, samplesByAltId);

                        //log replied to the message
                        messagingGateway.replyPublish(replyInfo.getReplyTo(), updatedCmoSampleLabel);
                    }
                    if (interrupted && cmoLabelGeneratorReqReplyQueue.isEmpty()) {
                        break;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    LOG.error("Error during request handling", e);
                }
            }
            cmoLabelGeneratorHandlerShutdownLatch.countDown();
        }
    }

    private List<SampleMetadata> getExistingPatientSamples(String cmoPatientId) throws Exception {
        Message reply = messagingGateway.request(PATIENT_SAMPLES_REQUEST_TOPIC,
                    cmoPatientId);
        SampleMetadata[] ptSamples = mapper.readValue(
                new String(reply.getData(), StandardCharsets.UTF_8),
                SampleMetadata[].class);
        return new ArrayList<>(Arrays.asList(ptSamples));
    }

    private List<SampleMetadata> getSamplesByAltId(String altId) throws Exception {
        Message reply = messagingGateway.request(SAMPLES_BY_ALT_ID_REQREPLY_TOPIC,
                    altId);
        SampleMetadata[] samplesByAltId = mapper.readValue(
                new String(reply.getData(), StandardCharsets.UTF_8),
                SampleMetadata[].class);
        return new ArrayList<>(Arrays.asList(samplesByAltId));
    }

    @Override
    public void initialize(Gateway gateway) throws Exception {
        if (!initialized) {
            messagingGateway = gateway;
            setupCmoSampleLabelGeneratorHandler(messagingGateway, this);
            initializeRequestReplyHandlers();
            initialized = true;
        } else {
            LOG.error("Messaging Handler Service has already been initialized, ignoring sample.\n");
        }
    }

    @Override
    public void newCmoSampleLabelGeneratorHandler(String sampleJson, String replyTo) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Message Handling Service has not been initialized");
        }
        if (!shutdownInitiated) {
            cmoLabelGeneratorReqReplyQueue.put(new ReplyInfo(sampleJson, replyTo));
        } else {
            LOG.error("Shutdown initiated, not accepting samples: " + sampleJson);
            throw new IllegalStateException("Shutdown initiated, not handling any more samples");
        }
    }

    private void setupCmoSampleLabelGeneratorHandler(Gateway gateway,
            RequestReplyHandlingServiceImpl requestReplyHandlingServiceImpl)
            throws Exception {
        gateway.replySub(CMO_LABEL_GENERATOR_REQREPLY_TOPIC, new MessageConsumer() {
            @Override
            public void onMessage(Message msg, Object message) {
                LOG.info("Received message on topic: " + CMO_LABEL_GENERATOR_REQREPLY_TOPIC);
                try {
                    requestReplyHandlingServiceImpl.newCmoSampleLabelGeneratorHandler(
                            new String(msg.getData()), msg.getReplyTo());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void initializeRequestReplyHandlers() throws Exception {
        cmoLabelGeneratorHandlerShutdownLatch = new CountDownLatch(NUM_NEW_REQUEST_HANDLERS);
        final Phaser cmoLabelGeneratorPhaser = new Phaser();
        cmoLabelGeneratorPhaser.register();
        for (int lc = 0; lc < NUM_NEW_REQUEST_HANDLERS; lc++) {
            cmoLabelGeneratorPhaser.register();
            exec.execute(new CmoLabelGeneratorReqReplyHandler(cmoLabelGeneratorPhaser));
        }
        cmoLabelGeneratorPhaser.arriveAndAwaitAdvance();
    }

    @Override
    public void shutdown() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Message Handling Service has not been initialized");
        }
        exec.shutdownNow();
        cmoLabelGeneratorHandlerShutdownLatch.await();
        shutdownInitiated = true;
    }

}
