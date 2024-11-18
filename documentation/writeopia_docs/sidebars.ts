import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars : SidebarsConfig = {
  sdkSidebar: [
    {
      type: 'doc',
      id: 'sdk/overview',
    },  
    {
      type: 'category',
      label: 'Getting started',
      items: ['sdk/tutorial-basics/basics', 'sdk/tutorial-basics/persistence'],
    },
    {
      type: 'category',
      label: 'Customize Drawing',
      items: ['sdk/customize-drawing/customize-drawers', 'sdk/customize-drawing/default-drawers', 'sdk/customize-drawing/default-types'],
    }, 
    {
      type: 'doc',
      id: 'sdk/customize-behaviour/customize-ui-commands',
    },
    {
      type: 'category',
      label: 'Text Commands',
      items: ['sdk/commands/default-commands', 'sdk/commands/command-samples', 'sdk/commands/customize-commands'],
    },
    {
      type: 'category',
      label: 'Export Notes',
      items: ['sdk/export/export-json', 'sdk/export/export-markdown'],
    },
    {
      type: 'doc',
      id: 'sdk/api_reference',
    }
  ],
  
  appSidebar: [
    {
      type: 'doc',
      id: 'application/overview-app',
    },
    {
      type: 'doc',
      label: 'Text Commands',
      id: 'application/commands/commands',
    },
    {
      type: 'doc',
      id: 'application/ui-commands/ui-commands',
    },
    {
      type: 'doc',
      id: 'application/slides/slides',
    }
  ]
  
};

export default sidebars;
