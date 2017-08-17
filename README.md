# hazelcastrepository-optimistic-issue
Example project to show a problem with optimistic hazelcastaggregationrepositories

When a org.apache.camel.processor.aggregate.hazelcast.HazelcastAggregationRepository is used with optimisticLocking aggregation is never completed because it failes to remove the exchange after the aggregation completes. (Just run the tests in this example to see)

It's is possibly due to the fact that in the #remove Method a new object is created like this
    DefaultExchangeHolder holder = DefaultExchangeHolder.marshal(exchange);
and then passed to 
    cache.remove(key, holder)

If i understand it correctly the object passed to cache remove must be the same that has been added to the hazelcast map before.
