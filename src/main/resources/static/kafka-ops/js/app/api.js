/* API client — all HTTP calls in one place */
var Api = {
    CONFIG_URL: '/kafka-ops/api/config',
    basePath: null,

    /* Extract a readable error message from Mithril's rejection or Spring Boot error response */
    extractError: function (e) {
        if (e && e.response) {
            var r = e.response;
            if (r.message) return r.message;
            if (r.error) return r.error + (r.status ? ' (' + r.status + ')' : '');
        }
        if (e && e.message) return e.message;
        if (typeof e === 'string') return e;
        return 'Unknown error';
    },

    disabled: false,

    init: function () {
        return m.request({ method: 'GET', url: this.CONFIG_URL }).then(function (config) {
            var url = config.retryEndpointUrl || 'operational/consumer-retries';
            Api.basePath = '/' + url.replace(/^\/+/, '');
            if (config.dltRouting) {
                AppState.dltRouting = config.dltRouting;
            }
        }).catch(function () {
            Api.disabled = true;
            throw new Error('Console is not enabled. Set kafka.ops.console.enabled=true in your application configuration.');
        });
    },

    getConsumers: function () {
        return m.request({ method: 'GET', url: this.basePath + '/consumers' });
    },

    poll: function (topicName, partition, offset) {
        return m.request({
            method: 'GET',
            url: this.basePath,
            params: { topicName: topicName, partition: partition, offset: offset }
        });
    },

    retry: function (topic, partition, offset) {
        return m.request({
            method: 'POST',
            url: this.basePath,
            body: { topic: topic, partition: Number(partition), offset: Number(offset) }
        });
    },

    sendCorrection: function (topic, key, value) {
        return m.request({
            method: 'POST',
            url: this.basePath + '/corrections/' + encodeURIComponent(topic),
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key: key, value: value }),
            serialize: function (v) { return v; }
        });
    },

    batchPoll: function (topicName, params) {
        return m.request({
            method: 'GET',
            url: this.basePath + '/batch',
            params: Object.assign({ topicName: topicName }, params)
        });
    },

    startDltRouting: function (topic) {
        return m.request({
            method: 'POST',
            url: this.basePath + '/dlt-routing/' + encodeURIComponent(topic) + '/start'
        });
    }
};
