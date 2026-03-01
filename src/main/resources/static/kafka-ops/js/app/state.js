/* Shared application state */
var AppState = {
    consumers: [],
    consumersLoading: true,
    selectedTopic: ''
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
