package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.model.AdvertisementContent;
import com.amazon.ata.advertising.service.model.EmptyGeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.GeneratedAdvertisement;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;
import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicateResult;
import com.amazon.ata.advertising.service.model.RequestContext;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Random;
import javax.inject.Inject;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {
    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     * @param contentDao Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
    }

    /**
     * Setter for Random class (optional).
     * @param random generates random numbers.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all of the content and metadata for the marketplace, determines which content the user is eligible for,
     * and returns the advertisement with the highest click through rate. If the user is not eligible for any ad,
     * returns an EmptyGeneratedAdvertisement.
     *
     * @param customerId - the customer for whom to generate a custom advertisement
     * @param marketplaceId - the id of the marketplace where the ad will be rendered
     * @return an ad with the highest CTR that the customer is eligible for, or an empty ad if none are eligible
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId) {
        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
            return new EmptyGeneratedAdvertisement();
        }

        List<AdvertisementContent> contents = contentDao.get(marketplaceId);
        if (CollectionUtils.isEmpty(contents)) {
            LOG.info("No ads found for marketplace {}. Returning empty ad.", marketplaceId);
            return new EmptyGeneratedAdvertisement();
        }

        RequestContext requestContext = new RequestContext(customerId, marketplaceId);
        TargetingEvaluator targetingEvaluator = new TargetingEvaluator(requestContext);

        Map<AdvertisementContent, Double> contentToMaxCtr = new HashMap<>();

        for (AdvertisementContent content : contents) {
            double maxCtr = -1.0;
            boolean userMatchesAnyGroup = false;

            List<TargetingGroup> targetingGroups = targetingGroupDao.get(content.getContentId());
            if (CollectionUtils.isNotEmpty(targetingGroups)) {
                for (TargetingGroup group : targetingGroups) {
                    if (targetingEvaluator.evaluate(group) == TargetingPredicateResult.TRUE) {
                        userMatchesAnyGroup = true;
                        if (group.getClickThroughRate() > maxCtr) {
                            maxCtr = group.getClickThroughRate();
                        }
                    }
                }
            }

            if (userMatchesAnyGroup) {
                contentToMaxCtr.put(content, maxCtr);
            }
        }

        if (contentToMaxCtr.isEmpty()) {
            LOG.info("User not eligible for any ads in marketplace {}. Returning empty ad.", marketplaceId);
            return new EmptyGeneratedAdvertisement();
        }

        Comparator<Double> descendingOrder = (ctr1, ctr2) -> Double.compare(ctr2, ctr1);
        TreeMap<Double, AdvertisementContent> sortedByCtr = new TreeMap<>(descendingOrder);

        for (Map.Entry<AdvertisementContent, Double> entry : contentToMaxCtr.entrySet()) {
            sortedByCtr.put(entry.getValue(), entry.getKey());
        }

        Map.Entry<Double, AdvertisementContent> bestAdEntry = sortedByCtr.firstEntry();
        AdvertisementContent bestAd = bestAdEntry.getValue();
        LOG.info("Selected ad with CTR {} for user {} in marketplace {}", bestAdEntry.getKey(), customerId, marketplaceId);

        return new GeneratedAdvertisement(bestAd);
    }
}

