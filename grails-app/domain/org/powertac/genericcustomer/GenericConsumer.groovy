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

import java.util.List

import org.joda.time.Instant
import org.powertac.common.AbstractCustomer
import org.powertac.common.Tariff
import org.powertac.common.TariffSubscription
import org.powertac.common.TimeService
import org.powertac.common.Timeslot
import org.powertac.common.configurations.GenericConstants

/**
 * Abstract customer implementation
 * @author Antonios Chrysopoulos
 */
class GenericConsumer extends AbstractCustomer{

  //============================= CONSUMPTION - PRODUCTION =================================================

  /** The first implementation of the power consumption function.
   *  I utilized the mean consumption of a neighborhood of households with a random variable */
  void consumePower()
  {
    Timeslot ts =  Timeslot.currentTimeslot()
    double summary = 0
    subscriptions.each { sub ->
      if (ts == null) summary = getConsumptionByTimeslot(sub)
      else summary = getConsumptionByTimeslot(ts.serialNumber)
      log.info "Consumption Load: ${summary} / ${subscriptions.size()} "
      sub.usePower(summary/subscriptions.size())
    }
  }


  double getConsumptionByTimeslot(int serial) {

    int hour = (int) (serial % GenericConstants.HOURS_OF_DAY)
    double ran = 0,summary = 0

    log.info " Hour: ${hour} "
    for (int i = 0; i < population;i++){
      if (hour < GenericConstants.MORNING_START_HOUR)
      {
        ran = GenericConstants.MEAN_NIGHT_CONSUMPTION + Math.random()
        summary = summary + ran
      }
      else if (hour < GenericConstants.EVENING_START_HOUR){
        ran = GenericConstants.MEAN_MORNING_CONSUMPTION + Math.random()
        summary = summary + ran
      }
      else {
        ran = GenericConstants.MEAN_EVENING_CONSUMPTION + Math.random()
        summary = summary + ran
      }
      log.info "Summary: ${summary}"
      return summary*GenericConstants.THOUSAND
    }
  }

  double getConsumptionByTimeslot(TariffSubscription sub) {

    int hour = timeService.getHourOfDay()
    double ran = 0, summary = 0
    log.info "Hour: ${hour} "

    for (int i = 0; i < sub.customersCommitted;i++){
      if (hour < GenericConstants.MORNING_START_HOUR)
      {
        ran = GenericConstants.MEAN_NIGHT_CONSUMPTION + Math.random()
        summary = summary + ran
      }
      else if (hour < GenericConstants.EVENING_START_HOUR){
        ran = GenericConstants.MEAN_MORNING_CONSUMPTION + Math.random()
        summary = summary + ran
      }
      else {
        ran = GenericConstants.MEAN_EVENING_CONSUMPTION + Math.random()
        summary = summary + ran
      }
      log.info "Summary: ${summary}"
      return summary*GenericConstants.THOUSAND
    }
  }

  void simpleEvaluationNewTariffs(List<Tariff> newTariffs) {

    // if there are no current subscriptions, then this is the
    // initial publication of default tariffs
    if (subscriptions == null || subscriptions.size() == 0) {
      subscribeDefault()
      return
    }

    double minEstimation = Double.POSITIVE_INFINITY
    int index = 0, minIndex = 0

    //adds current subscribed tariffs for reevaluation
    def evaluationTariffs = new ArrayList(newTariffs)
    Collections.copy(evaluationTariffs,newTariffs)
    evaluationTariffs.addAll(subscriptions?.tariff)


    log.debug("Estimation size for ${this.toString()}= " + evaluationTariffs.size())
    if (evaluationTariffs.size()> 1) {
      evaluationTariffs.each { tariff ->
        log.info "Tariff : ${tariff.toString()} Tariff Type : ${tariff.powerType}"
        if (tariff.isExpired() == false && customerInfo.powerTypes.find{tariff.powerType == it} ){
          minEstimation = (double)Math.min(minEstimation,this.costEstimation(tariff))
          minIndex = index
        }
        index++
      }
      log.info "Tariff:  ${evaluationTariffs.getAt(minIndex).toString()} Estimation = ${minEstimation} "

      subscriptions.each { sub ->
        log.info "Equality: ${sub.tariff.tariffSpec} = ${evaluationTariffs.getAt(minIndex).tariffSpec} "
        if (!(sub.tariff.tariffSpec == evaluationTariffs.getAt(minIndex).tariffSpec)) {
          log.info "Existing subscription ${sub.toString()}"
          int populationCount = sub.customersCommitted
          this.subscribe(evaluationTariffs.getAt(minIndex),  populationCount)
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
          estimation.add(-(costEstimation(tariff)))
        }
        else estimation.add(Double.NEGATIVE_INFINITY)
      }
      int minIndex = logitPossibilityEstimation(estimation)

      subscriptions.each { sub ->
        log.info "Equality: ${sub.tariff.tariffSpec} = ${evaluationTariffs.getAt(minIndex).tariffSpec} "
        if (!(sub.tariff.tariffSpec == evaluationTariffs.getAt(minIndex).tariffSpec)) {
          log.info "Existing subscription ${sub.toString()}"
          int populationCount = sub.customersCommitted
          this.unsubscribe(sub, populationCount)
          this.subscribe(evaluationTariffs.getAt(minIndex),  populationCount)
        }
      }
      this.save()
    }
  }

  double costEstimation(Tariff tariff)
  {
    double costVariable = estimateVariableTariffPayment(tariff)
    double costFixed = estimateFixedTariffPayments(tariff)
    return (costVariable + costFixed)/GenericConstants.MILLION
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

    double costSummary = 0
    double summary = 0, cumulativeSummary = 0

    int serial = ((timeService.currentTime.millis - timeService.base) / TimeService.HOUR)
    Instant base = timeService.currentTime - serial*TimeService.HOUR
    int day = (int) (serial / GenericConstants.HOURS_OF_DAY) + 1 // this will be changed to one or more random numbers
    Instant now = base + day * TimeService.DAY

    for (int i=0;i < GenericConstants.HOURS_OF_DAY;i++){
      summary = getConsumptionByTimeslot(i)
      cumulativeSummary += summary
      costSummary += tariff.getUsageCharge(now,summary,cumulativeSummary)
      log.info "Time:  ${now.toString()} costSummary: ${costSummary} "
      now = now + TimeService.HOUR
    }
    log.info "Variable cost Summary: ${costSummary}"
    return costSummary

  }

  int logitPossibilityEstimation(Vector estimation) {

    double lamda = 2500 // 0 the random - 10 the logic
    double summedEstimations = 0
    Vector randomizer = new Vector()
    int[] possibilities = new int[estimation.size()]

    for (int i=0;i < estimation.size();i++){
      summedEstimations += Math.pow(GenericConstants.EPSILON,lamda*estimation.get(i))
      "Cost variable: ${estimation.get(i)}"
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
        bootstrap[i][j] = (long)getConsumptionByTimeslot(i*GenericConstants.HOURS_OF_DAY + j)
      }
    }

    return bootstrap
  }

  void step(){
    this.checkRevokedSubscriptions()
    this.consumePower()
  }
}
