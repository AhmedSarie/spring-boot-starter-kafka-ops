/* Shared application state */
var AppState = {
    consumers: [],          /* Array of KafkaOpsConsumerInfo objects */
    consumersLoading: true,
    selectedTopic: '',
    dltRouting: null,       /* DLT routing config from /kafka-ops/api/config */

    /* Find the consumer info object for a given topic name (main, DLT, or retry) */
    findConsumer: function (topicName) {
        for (var i = 0; i < this.consumers.length; i++) {
            var c = this.consumers[i];
            if (c.name === topicName) return c;
            if (c.dlt && c.dlt.name === topicName) return c;
            if (c.retry && c.retry.name === topicName) return c;
        }
        return null;
    },

    /* Find the topic info (partitions, messageCount, formats) for any topic name (main, DLT, or retry) */
    findTopicInfo: function (topicName) {
        for (var i = 0; i < this.consumers.length; i++) {
            var c = this.consumers[i];
            if (c.name === topicName) return { partitions: c.partitions, messageCount: c.messageCount, keyFormat: c.keyFormat, valueFormat: c.valueFormat };
            if (c.dlt && c.dlt.name === topicName) return { partitions: c.dlt.partitions, messageCount: c.dlt.messageCount, keyFormat: c.keyFormat, valueFormat: c.valueFormat };
            if (c.retry && c.retry.name === topicName) return { partitions: c.retry.partitions, messageCount: c.retry.messageCount, keyFormat: c.keyFormat, valueFormat: c.valueFormat };
        }
        return null;
    },

    /* Map codec format code to display name */
    formatLabel: function (fmt) {
        if (!fmt) return '';
        var labels = { avro: 'Avro', proto: 'Protobuf', json: 'Json', string: 'String' };
        return labels[fmt] || fmt;
    },

    /* Format a message count for display (e.g. 1500 → "1.5K") */
    formatCount: function (count) {
        if (count === undefined || count === null) return '';
        if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M';
        if (count >= 1000) return (count / 1000).toFixed(1) + 'K';
        return String(count);
    },

    /* Re-fetch consumer metadata from the API (non-blocking, updates in background) */
    refreshConsumers: function () {
        if (!Api.basePath || Api.disabled) return;
        Api.getConsumers().then(function (data) {
            if (Array.isArray(data)) {
                AppState.consumers = data.sort(function (a, b) {
                    var nameA = (typeof a === 'string') ? a : (a.name || '');
                    var nameB = (typeof b === 'string') ? b : (b.name || '');
                    return nameA.localeCompare(nameB);
                });
            }
        }).catch(function () { /* ignore — non-critical refresh */ });
    },

    /* Find the main topic name for a given topic (DLT or retry resolves to parent) */
    findMainTopicName: function (topicName) {
        for (var i = 0; i < this.consumers.length; i++) {
            var c = this.consumers[i];
            if (c.name === topicName) return c.name;
            if (c.dlt && c.dlt.name === topicName) return c.name;
            if (c.retry && c.retry.name === topicName) return c.name;
        }
        return topicName;
    }
};

/* Per-topic poll history stored in localStorage. Max 10 entries per topic. */
var PollHistory = {
    MAX: 10,
    PREFIX: 'kafka-ops-history:',

    get: function (topic) {
        try {
            var raw = localStorage.getItem(this.PREFIX + topic);
            return raw ? JSON.parse(raw) : [];
        } catch (e) {
            return [];
        }
    },

    add: function (topic, partition, offset) {
        var entries = this.get(topic);
        var key = partition + ':' + offset;
        entries = entries.filter(function (e) { return e.partition + ':' + e.offset !== key; });
        entries.unshift({ partition: partition, offset: offset });
        if (entries.length > this.MAX) entries = entries.slice(0, this.MAX);
        try {
            localStorage.setItem(this.PREFIX + topic, JSON.stringify(entries));
        } catch (e) { /* storage full — ignore */ }
    }
};
