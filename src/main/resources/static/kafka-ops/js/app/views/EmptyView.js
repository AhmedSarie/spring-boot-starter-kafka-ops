/* Landing page — shown when no consumer is selected */
var EmptyView = {
    view: function () {
        return m(Layout, [
            m('.empty-state', [
                m.trust('<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>'),
                m('p', 'Select a topic from the sidebar to get started')
            ])
        ]);
    }
};
