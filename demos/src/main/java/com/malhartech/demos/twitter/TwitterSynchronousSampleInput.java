/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.demos.twitter;

import com.malhartech.annotation.ModuleAnnotation;
import com.malhartech.annotation.PortAnnotation;
import com.malhartech.annotation.ShipContainingJars;
import com.malhartech.dag.AbstractInputModule;
import com.malhartech.dag.AbstractSynchronousInputModule;
import com.malhartech.dag.Component;
import com.malhartech.dag.ModuleConfiguration;
import com.malhartech.dag.Sink;
import com.malhartech.util.CircularBuffer;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
@ModuleAnnotation(ports = {
  @PortAnnotation(name = TwitterSynchronousSampleInput.OPORT_STATUS, type = PortAnnotation.PortType.OUTPUT),
  @PortAnnotation(name = TwitterSynchronousSampleInput.OPORT_TEXT, type = PortAnnotation.PortType.OUTPUT),
  @PortAnnotation(name = TwitterSynchronousSampleInput.OPORT_USER_MENTION, type = PortAnnotation.PortType.OUTPUT),
  @PortAnnotation(name = TwitterSynchronousSampleInput.OPORT_URL, type = PortAnnotation.PortType.OUTPUT),
  @PortAnnotation(name = TwitterSynchronousSampleInput.OPORT_HASHTAG, type = PortAnnotation.PortType.OUTPUT),
  @PortAnnotation(name = TwitterSynchronousSampleInput.OPORT_MEDIA, type = PortAnnotation.PortType.OUTPUT)
})
@ShipContainingJars(classes = {StatusListener.class, Status.class})
public class TwitterSynchronousSampleInput extends AbstractSynchronousInputModule implements StatusListener
{
  private static final Logger logger = LoggerFactory.getLogger(TwitterSynchronousSampleInput.class);
  /**
   * names of the output ports to be used in the annotations.
   */
  public static final String OPORT_STATUS = "status";
  public static final String OPORT_TEXT = "text";
  public static final String OPORT_USER_MENTION = "user_mention";
  public static final String OPORT_URL = "url";
  public static final String OPORT_HASHTAG = "hashtag";
  public static final String OPORT_MEDIA = "media";
  /**
   * direct access to the sinks for efficiency as opposed to emitting.
   */
  protected transient Sink status;
  protected transient Sink text;
  protected transient Sink userMention;
  protected transient Sink url;
  protected transient Sink hashtag;
  protected transient Sink media;
  /**
   * For tapping into the tweets.
   */
  transient TwitterStream ts;
  transient int count;
  transient int multiplier;

  @Override
  public void setup(ModuleConfiguration config)
  {
    multiplier = config.getInt("FeedMultiplier", 1);
    if (multiplier != 1) {
      logger.info("Load set to be {}% of the entire twitter feed", multiplier);
    }

    ConfigurationBuilder cb = new ConfigurationBuilder();
    cb.setDebugEnabled(config.getBoolean("twitter4j.debug", false)).
            setOAuthConsumerKey(config.get("twitter4j.oauth.consumerKey")).
            setOAuthConsumerSecret(config.get("twitter4j.oauth.consumerSecret")).
            setOAuthAccessToken(config.get("twitter4j.oauth.accessToken")).
            setOAuthAccessTokenSecret(config.get("twitter4j.oauth.accessTokenSecret"));

    ts = new TwitterStreamFactory(cb.build()).getInstance();
    ts.addListener(this);
    // we can only listen to tweets containing links by callng ts.links().
    // it seems it requires prior signed agreement with twitter.
    ts.sample();
  }

  @Override
  public void teardown()
  {
    ts.shutdown();
    ts = null;
  }

  @Override
  public void onStatus(Status status)
  {
    for (int i = multiplier; i-- > 0;) {
      count++;
      emit(OPORT_STATUS, status);
    }
  }

  @Override
  public void endWindow()
  {
    if (count % (16 * multiplier) == 0) {
      logger.debug("processed {} statuses", count);
    }
  }

  @Override
  public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice)
  {
    // do nothing
  }

  @Override
  public void onTrackLimitationNotice(int numberOfLimitedStatuses)
  {
    // do nothing
  }

  @Override
  public void onScrubGeo(long userId, long upToStatusId)
  {
    // do nothing
  }

  @Override
  public void onException(Exception excptn)
  {
    logger.info("Stopping samping because {}", excptn.getLocalizedMessage());
    synchronized (this) {
      this.notifyAll();
    }
  }

  @Override
  public synchronized void run()
  {
    try {
      this.wait();
    }
    catch (InterruptedException ex) {
      logger.info("{} exiting generation of the input since got interrupted with {}", this, ex);
    }
  }
}
