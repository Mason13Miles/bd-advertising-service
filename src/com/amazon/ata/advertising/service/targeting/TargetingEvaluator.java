package com.amazon.ata.advertising.service.targeting;

import com.amazon.ata.advertising.service.model.RequestContext;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;
import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicate;
import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Evaluates TargetingPredicates for a given RequestContext.
 */
public class TargetingEvaluator {
    public static final boolean IMPLEMENTED_STREAMS = true;
    public static final boolean IMPLEMENTED_CONCURRENCY = true;
    private final RequestContext requestContext;
    private static final int THREAD_POOL_SIZE = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    /**
     * Creates an evaluator for targeting predicates.
     * @param requestContext Context that can be used to evaluate the predicates.
     */
    public TargetingEvaluator(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    /**
     * Evaluate a TargetingGroup to determine if all of its TargetingPredicates are TRUE or not for the given
     * RequestContext.
     * @param targetingGroup Targeting group for an advertisement, including TargetingPredicates.
     * @return TRUE if all of the TargetingPredicates evaluate to TRUE against the RequestContext, FALSE otherwise.
     */
    public TargetingPredicateResult evaluate(TargetingGroup targetingGroup) {
        List<TargetingPredicate> predicates = targetingGroup.getTargetingPredicates();

        try {
            List<Future<TargetingPredicateResult>> futures = predicates.stream()
                    .map(predicate -> executorService.submit(() -> predicate.evaluate(requestContext)))
                    .collect(Collectors.toList());

            return futures.stream()
                    .map(this::getFutureResult)
                    .filter(result -> result != TargetingPredicateResult.TRUE)
                    .findFirst()
                    .orElse(TargetingPredicateResult.TRUE);
        } catch (Exception e) {
            e.printStackTrace();
            return TargetingPredicateResult.FALSE;
        }
    }

    private TargetingPredicateResult getFutureResult(Future<TargetingPredicateResult> future) {
        try {
            TargetingPredicateResult result = future.get();
            return result == TargetingPredicateResult.INDETERMINATE ? TargetingPredicateResult.FALSE : result;
        } catch (Exception e) {
            e.printStackTrace();
            return TargetingPredicateResult.FALSE;
        }
    }
}


