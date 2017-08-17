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

public class SimpleHazelcastAggregationTest extends CamelTestSupport {

  private HazelcastAggregationRepository aggrRepo;
  
  @Produce(uri="direct:start")
  private ProducerTemplate startTemplate;
  
  @EndpointInject(uri = "mock:resultMock")
  private MockEndpoint resultMock;
  
  private HazelcastInstance hzInst;
  
  public SimpleHazelcastAggregationTest() {
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
        
        from("direct:start")
          .aggregate(header("MyId"), new AggregationStrategy() {
              @Override
              public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                return newExchange;
              }
            })
            .completionSize(2)
            .optimisticLocking()
            .aggregationRepository(aggrRepo)
          .to(resultMock);
      }
    };
  }

  @Test
  public void test() throws InterruptedException {
    
    resultMock.expectedMessageCount(1);
    
    startTemplate.sendBodyAndHeader("Test", "MyId", "1");
    startTemplate.sendBodyAndHeader("Test", "MyId", "1");
    resultMock.assertIsSatisfied();
    
  }
}
