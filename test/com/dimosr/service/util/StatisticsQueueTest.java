package com.dimosr.service.util;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StatisticsQueueTest {

    private enum TestEnum {
        VALUE_1,
        VALUE_2
    }

    private static final int QUEUE_SIZE = 5;
    private StatisticsQueue<TestEnum> statisticsQueue;

    @Before
    public void setupQueue() {
        statisticsQueue = new StatisticsQueue<>(QUEUE_SIZE);
    }

    @Test
    public void testQueueIsNotFullInitially() {
        assertThat(statisticsQueue.isFull()).isFalse();
    }

    @Test
    public void testQueueGetsFullIfWeAddItemsMoreThanTheSize() {
        for(int i = 0; i < QUEUE_SIZE; i++) {
            statisticsQueue.addItem(TestEnum.VALUE_1);
        }

        assertThat(statisticsQueue.isFull()).isTrue();
    }

    @Test
    public void testQueueIsEmptiedWhenClearing() {
        for(int i = 0; i < QUEUE_SIZE; i++) {
            statisticsQueue.addItem(TestEnum.VALUE_1);
        }
        statisticsQueue.clear();

        assertThat(statisticsQueue.isFull()).isFalse();
    }

    @Test
    public void testOccurencesAreInitiallyZero() {
        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_1)).isEqualTo(0);
        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_2)).isEqualTo(0);
    }

    @Test
    public void testGetOccurencesReflectsAddedItems() {
        statisticsQueue.addItem(TestEnum.VALUE_1);
        statisticsQueue.addItem(TestEnum.VALUE_2);
        statisticsQueue.addItem(TestEnum.VALUE_2);

        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_1)).isEqualTo(1);
        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_2)).isEqualTo(2);
    }

    @Test
    public void testOccurencesAreZeroedAfterClearing() {
        statisticsQueue.addItem(TestEnum.VALUE_1);
        statisticsQueue.addItem(TestEnum.VALUE_2);
        statisticsQueue.clear();

        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_1)).isEqualTo(0);
        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_2)).isEqualTo(0);
    }

    @Test
    public void testOccurencesAreUpdatedWhenQueueIsFullAndItemsArePoppedOut() {
        for(int i = 0; i < QUEUE_SIZE; i++) {
            statisticsQueue.addItem(TestEnum.VALUE_1);
        }
        statisticsQueue.addItem(TestEnum.VALUE_2);

        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_1)).isEqualTo(QUEUE_SIZE-1);
        assertThat(statisticsQueue.getOccurences(TestEnum.VALUE_2)).isEqualTo(1);
    }
}
