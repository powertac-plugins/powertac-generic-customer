/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.powertac.genericcustomer

import java.util.Collections
import java.util.Random

import org.joda.time.Instant
import org.powertac.common.AbstractCustomer
import org.powertac.common.Broker
import org.powertac.common.Tariff
import org.powertac.common.TariffSubscription
import org.powertac.common.TimeService
import org.powertac.common.Timeslot
import org.powertac.common.configurations.GenericConstants
import org.powertac.common.enumerations.PowerType

/**
 * Abstract customer implementation
 * @author Antonios Chrysopoulos
 */
class GenericProducer extends AbstractCustomer{


  //============================= CONSUMPTION - PRODUCTION =================================================

  /** The first implementation of the power consumption function.
   *  I utilized the mean consumption of a neighborhood of households with a random variable */
  void producePower()
  {
    Timeslot ts =  Timeslot.currentTimeslot()
    double summary = 0
    subscriptions.each { sub ->
      if (ts == null) summary = getProductionByTimeslot(sub)
      else summary = getProductionByTimeslot(ts.serialNumber)
      log.info "Production Load: ${summary} / ${subscriptions.size()} "

      sub.usePower(-(summary/subscriptions.size()))
    }
  }

  /** In this function, the broker calls for a certain amount of energy as it has predicted and
   *  the producer produces this amount in return */
  void producePower(Broker broker, double amount)
  {

    List<TariffSubscription> subs = []

    subscriptions.each { sub ->
      if (sub.tariff.broker == broker) subs.add(sub)
    }

    if (subs.size() == 0) log.info "Not subscribed to broker ${broker.toString()}"
    else {
      subs.each { sub ->
        log.info "Production Load: ${amount}/${subs.size()} "
        sub.usePower(-(amount/subs.size()))
      }
    }
  }

  /** In this function, the broker calls for a certain amount of energy as it has predicted and
   *  the producer produces this amount in return */
  void producePower(Tariff tariff, double amount)
  {

    List<TariffSubscription> subs = []

    subscriptions.each { sub ->
      if (sub.tariff == tariff) subs.add(sub)
    }

    if (subs.size() == 0) log.info "Not subscribed to broker ${broker.toString()}"
    else {
      subs.each { sub ->
        log.info "Production Load: ${amount}/${subs.size()} "
        sub.usePower(-(amount/subs.size()))
      }
    }
  }


  double getProductionByTimeslot(int serial) {

    int hour = (int) (serial % GenericConstants.HOURS_OF_DAY)

    log.info " Hour: ${hour} "

    Random ran = new Random()
    return GenericConstants.THOUSAND * ran.nextFloat()

  }

  double getProductionByTimeslot(TariffSubscription sub) {

    int hour = timeService.getHourOfDay()
    log.info "Hour: ${hour} "

    Random ran = new Random()
    return GenericConstants.THOUSAND * ran.nextFloat()

  }

  void simpleEvaluationNewTariffs(List<Tariff> newTariffs) {

    // if there are no current subscriptions, then this is the
    // initial publication of default tariffs
    if (subscriptions == null || subscriptions.size() == 0) {
      subscribeDefault()
      return
    }

    double maxEstimation = Double.NEGATIVE_INFINITY
    int index = 0, maxIndex = 0

    //adds current subscribed tariffs for reevaluation
    def evaluationTariffs = new ArrayList(newTariffs)
    Collections.copy(evaluationTariffs,newTariffs)
    evaluationTariffs.addAll(subscriptions?.tariff)

    log.debug("Estimation size for ${this.toString()}= " + evaluationTariffs.size())
    if (evaluationTariffs.size()> 1) {
      evaluationTariffs.each { tariff ->
        log.info "Tariff : ${tariff.toString()} Tariff Type : ${tariff.powerType}"
        if (tariff.isExpired() == false && customerInfo.powerTypes.find{tariff.powerType == it} ){
          maxEstimation = (double)Math.max(maxEstimation,this.paymentEstimation(tariff))
          maxIndex = index
        }
        index++
      }
      log.info "Tariff:  ${evaluationTariffs.getAt(maxIndex).toString()} Estimation = ${maxEstimation} "

      subscriptions.each { sub ->
        log.info "Equality: ${sub.tariff.tariffSpec} = ${evaluationTariffs.getAt(maxIndex).tariffSpec} "
        if (!(sub.tariff.tariffSpec == evaluationTariffs.getAt(maxIndex).tariffSpec)) {
          log.info "Existing subscription ${sub.toString()}"
          int populationCount = sub.customersCommitted
          this.subscribe(evaluationTariffs.getAt(maxIndex),  populationCount)
          this.unsubscribe(sub, populationCount)
        }
      }
      this.save()
    }
  }


  void possibilityEvaluationNewTariffs(List<Tariff> newTariffs)
  {
    // if there are no current subscriptions, then this is the
    // initial publication of default tariffs
    if (subscriptions == null || subscriptions.size() == 0) {
      subscribeDefault()
      return
    }

    log.info "Tariffs: ${Tariff.list().toString()}"
    Vector estimation = new Vector()
    //adds current subscribed tariffs for reevaluation
    def evaluationTariffs = new ArrayList(newTariffs)
    Collections.copy(evaluationTariffs,newTariffs)
    evaluationTariffs.addAll(subscriptions?.tariff)

    log.debug("Estimation size for ${this.toString()}= " + evaluationTariffs.size())
    if (evaluationTariffs.size()> 1) {
      evaluationTariffs.each { tariff ->
        log.info "Tariff : ${tariff.toString()} Tariff Type : ${tariff.powerType} Tariff Expired : ${tariff.isExpired()}"
        if (!tariff.isExpired() && customerInfo.powerTypes.find{tariff.powerType == it}) {
          estimation.add(paymentEstimation(tariff))
        }
        else estimation.add(Double.POSITIVE_INFINITY)
      }
      int maxIndex = logitPossibilityEstimation(estimation)
      subscriptions.each { sub ->
        log.info "Equality: ${sub.tariff.tariffSpec} = ${evaluationTariffs.getAt(maxIndex).tariffSpec} "
        if (!(sub.tariff.tariffSpec == evaluationTariffs.getAt(maxIndex).tariffSpec)) {
          log.info "Existing subscription ${sub.toString()}"
          int populationCount = sub.customersCommitted
          this.unsubscribe(sub, populationCount)
          this.subscribe(evaluationTariffs.getAt(maxIndex),  populationCount)
        }
      }
      this.save()
    }
  }

  double paymentEstimation(Tariff tariff)
  {
    double paymentVariable = estimateVariableTariffPayment(tariff)
    double paymentFixed = estimateFixedTariffPayments(tariff)
    return (paymentVariable + paymentFixed)/GenericConstants.MILLION
  }

  double estimateFixedTariffPayments(Tariff tariff)
  {
    double lifecyclePayment = (double)tariff.getEarlyWithdrawPayment() + (double)tariff.getSignupPayment()
    double minDuration

    // When there is not a Minimum Duration of the contract, you cannot divide with the duration because you don't know it.
    if (tariff.getMinDuration() == 0) minDuration = GenericConstants.MEAN_TARIFF_DURATION * TimeService.DAY
    else minDuration = tariff.getMinDuration()

    log.info("Minimum Duration: ${minDuration}")
    return ((double)tariff.getPeriodicPayment() + (lifecyclePayment / minDuration))
  }

  double estimateVariableTariffPayment(Tariff tariff){

    double paymentSummary = 0
    double summary = 0, cumulativeSummary = 0

    int serial = ((timeService.currentTime.millis - timeService.base) / TimeService.HOUR)
    Instant base = timeService.currentTime - serial*TimeService.HOUR
    int day = (int) (serial / GenericConstants.HOURS_OF_DAY) + 1 // this will be changed to one or more random numbers
    Instant now = base + day * TimeService.DAY

    for (int i=0;i < GenericConstants.HOURS_OF_DAY;i++){
      summary = getProductionByTimeslot(i)
      cumulativeSummary += summary
      paymentSummary += tariff.getUsageCharge(now,summary,cumulativeSummary)
      log.info "Time:  ${now.toString()} paymentSummary: ${paymentSummary} "
      now = now + TimeService.HOUR
    }
    log.info "Variable payment Summary: ${paymentSummary}"
    return paymentSummary

  }

  int logitPossibilityEstimation(Vector estimation) {
    double lamda = 2500 // 0 the random - 10 the logic
    double summedEstimations = 0
    Vector randomizer = new Vector()
    log.info(estimation.toString())
    int[] possibilities = new int[estimation.size()]
    for (int i=0;i < estimation.size();i++){
      summedEstimations += Math.pow(GenericConstants.EPSILON,lamda*estimation.get(i))
      "Payment variable: ${estimation.get(i)}"
      log.info"Summary of Estimation: ${summedEstimations}"
    }
    for (int i = 0;i < estimation.size();i++){
      possibilities[i] = (int)(GenericConstants.PERCENTAGE *(Math.pow(GenericConstants.EPSILON,lamda*estimation.get(i)) / summedEstimations))
      for (int j=0;j < possibilities[i]; j++){
        randomizer.add(i)
      }
    }
    log.info "Randomizer Vector: ${randomizer}"
    log.info "Possibility Vector: ${possibilities.toString()}"
    int index = randomizer.get((int)(randomizer.size()*Math.random()))
    log.info "Resulting Index = ${index}"
    return index
  }

  def getBootstrapData(){

    long[][] bootstrap = new long[GenericConstants.DAYS_OF_BOOTSTRAP][GenericConstants.HOURS_OF_DAY]

    for (int i = 0;i < GenericConstants.DAYS_OF_BOOTSTRAP;i++){
      for (int j = 0;j < GenericConstants.HOURS_OF_DAY;j++){
        bootstrap[i][j] = (long)getProductionByTimeslot(i*GenericConstants.HOURS_OF_DAY + j)
      }
    }

    return bootstrap
  }

  void step(){
    this.checkRevokedSubscriptions()
    this.producePower()
  }
}
