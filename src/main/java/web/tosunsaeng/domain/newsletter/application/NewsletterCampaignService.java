package web.tosunsaeng.domain.newsletter.application;

public interface NewsletterCampaignService {

    void reconcileCampaigns();

    void processNextScheduledCampaign();

    void processNextStaleCampaign();

    void completeNextCampaign();

    boolean cancelScheduledCampaign(String campaignId);

    boolean reopenFailedCampaign(String campaignId);
}
