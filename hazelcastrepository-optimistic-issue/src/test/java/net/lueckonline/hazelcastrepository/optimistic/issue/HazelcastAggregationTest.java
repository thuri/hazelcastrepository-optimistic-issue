package net.lueckonline.hazelcastrepository.optimistic.issue;

import java.util.UUID;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.hazelcast.HazelcastAggregationRepository;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import com.hazelcast.core.HazelcastInstance;

public class HazelcastAggregationTest extends CamelTestSupport {

  private HazelcastAggregationRepository aggrRepo;
  
  @Produce(uri = "seda:request")
  private ProducerTemplate sedaRequest;
  
  @Produce(uri = "seda:response")
  private ProducerTemplate sedaResponse;
  
  @EndpointInject(uri="mock:requestMock")
  private MockEndpoint requestMock;
  
  @EndpointInject(uri = "mock:resultMock")
  private MockEndpoint resultMock;
  
  private HazelcastInstance hzInst;
  
  public HazelcastAggregationTest() {
    super();
    hzInst = new com.hazelcast.test.TestHazelcastInstanceFactory().newHazelcastInstance();
  }

  @Override
  protected RouteBuilder createRouteBuilder() throws Exception {
    return new RouteBuilder() {
      
      @Override
      public void configure() throws Exception {

        aggrRepo = new HazelcastAggregationRepository("aggrRepo", true, hzInst);
        
        getContext().setTracing(true);
        
        from("seda:request")
          .setHeader("MyId", constant(UUID.randomUUID().toString()))
          .log("got request")
          .to("direct:aggregation")
          .to(requestMock);
        
        from("seda:response")
          .setHeader("isResponse", constant(true))
          .log("got response")
          .to("direct:aggregation");
        
        from("direct:aggregation")
          .aggregate(header("MyId"), new MyAggregationStrategy())
            .completionSize(2)
            .optimisticLocking()
            .aggregationRepository(aggrRepo)
          .to(resultMock);
      }
    };
  }

  @Test
  public void test() throws InterruptedException {

    requestMock.whenAnyExchangeReceived(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        sedaResponse.sendBodyAndHeader("Responsebody", "MyId", exchange.getIn().getHeader("MyId"));
      }
    });
    
    requestMock.expectedMessageCount(1);
    requestMock.expectedBodyReceived().body().equals("Requestbody");
    
    resultMock.expectedMessageCount(1);
    resultMock.expectedBodyReceived().body().equals("Responsebody");
    resultMock.expectedHeaderReceived("aggregated", true);
    
    sedaRequest.sendBody("Requestbody");
    
    requestMock.assertIsSatisfied();
    resultMock.assertIsSatisfied();
  }
  
  class MyAggregationStrategy implements AggregationStrategy {
    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
      Exchange responseExchange;
      Exchange requestExchange;

      // the first time we aggregate we only have the new exchange,
      if (oldExchange == null) {
        return newExchange;
      }

      // now find the exchange containing the external reply message
      if (newExchange.getIn().getHeader("isReponse", false, Boolean.class)) {
        // newExchange is the response exchange
        responseExchange = newExchange;
        requestExchange = oldExchange;
      } else {
        // oldExchange is the response exchange
        responseExchange = oldExchange;
        requestExchange = newExchange;
      }

      // copy the response to request exchange and leave headers and properties are unchanged
      requestExchange.getIn().setBody(responseExchange.getIn().getBody());
      requestExchange.getIn().setHeader("aggregated", true);
      requestExchange.removeProperty(Exchange.EXCEPTION_CAUGHT);

      return requestExchange;
    }
  }
}
