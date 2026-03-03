/* Shared application state */
var AppState = {
    consumers: [],          /* Array of KafkaOpsConsumerInfo objects */
    consumersLoading: true,
    selectedTopic: '',

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
