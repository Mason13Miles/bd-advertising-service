package com.amazon.ata.advertising.service.targeting.predicate;

import com.amazon.ata.advertising.service.exceptions.AdvertisementServiceException;
import com.amazon.ata.advertising.service.dependency.DaggerLambdaComponent;
import com.amazon.ata.advertising.service.dependency.LambdaComponent;
import com.amazon.ata.advertising.service.dependency.TargetingPredicateInjector;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to convert a list of the complex type TargetingPredicate to a string and vice-versa.
 */
public class TargetingPredicateTypeConverter implements DynamoDBTypeConverter<String, List<TargetingPredicate>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Static initialization ensures this is never null when DynamoDBMapper constructs the converter
    private static final LambdaComponent COMPONENT = DaggerLambdaComponent.create();

    /**
     * Serializes the passed predicate list into a String. Each member is serialized separately so that the Jackson
     * annotation (@JsonTypeInfo) can live at the abstract TargetingPredicate class, rather than annotating each
     * subclass.
     * @param predicateList - a list of TargetingPredicates that will be converted to a String value
     * @return The serialized string. "[]" in the case of an empty list.
     */
    @Override
    public String convert(List<TargetingPredicate> predicateList) {
        return new StringBuilder()
                .append("[")
                .append(predicateList.stream()
                        .map(this::getSerializePredicateFunction)
                        .collect(Collectors.joining(",")))
                .append("]")
                .toString();
    }

    private String getSerializePredicateFunction(TargetingPredicate predicate) {
        try {
            return MAPPER.writeValueAsString(predicate);
        } catch (IOException e) {
            throw new AdvertisementServiceException("Unable to convert the predicate to a String. "
                    + "Object: " + predicate, e);
        }
    }

    /**
     * Deserializes a JSON string into a List of TargetingPredicates, injecting dependencies via Dagger.
     *
     * @param value - The JSON string representing a list of TargetingPredicates
     * @return The reconstructed list of TargetingPredicates
     */
    @Override
    public List<TargetingPredicate> unconvert(String value) {
        // Use the static COMPONENT so it's never null
        TargetingPredicateInjector injector = COMPONENT.getTargetingPredicateInjector();
        try {
            final List<TargetingPredicate> predicates = MAPPER.readValue(
                    value,
                    new TypeReference<List<TargetingPredicate>>() {}
            );
            for (TargetingPredicate predicate : predicates) {
                injector.inject(predicate); // Inject dependencies into each predicate
            }
            return predicates;
        } catch (IOException e) {
            throw new AdvertisementServiceException("Unable to convert the String value to a list of targeting "
                    + "predicates. String: " + value, e);
        }
    }
}

