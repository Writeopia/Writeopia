import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Writeopia',
  tagline: 'Rich edit text for Kotlin',
  favicon: 'img/favicon.ico',
  url: 'https://docs.writeopia.io/',
  baseUrl: '/',
  organizationName: 'io.writeopia', // Usually your GitHub org/user name.
  projectName: 'Writeopia', // Usually your repo name.
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: require.resolve('./sidebars.ts'),          
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
      // Replace with your project's social card
      image: 'img/docusaurus-social-card.jpg',
      navbar: {
        title: 'Writeopia',
        logo: {
          alt: 'Writeopia Logo',
          src: 'img/logo.png',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'appSidebar',
            position: 'left',
            label: 'Application',
          },
          {
            type: 'docSidebar',
            sidebarId: 'sdkSidebar',
            position: 'left',
            label: 'SDK',
          },
          {
            type: 'docSidebar',
            sidebarId: 'localDevSidebar',
            position: 'left',
            label: 'Local Development',
          },
          {
            href: 'https://sample.writeopia.io/',
            label: 'Live Sample',
            position: 'left',
          },          
          {
            href: 'https://github.com/leandroBorgesFerreira/Writeopia',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {        
        links: [
          // {
          //   title: 'Docs',
          //   items: [
          //     {
          //       label: 'Tutorial',
          //       to: 'docs/category/getting-started-with-android',
          //     },
          //   ],
          // },
          // {
          //   title: 'Community',
          //   items: [
          //     {
          //       label: 'Stack Overflow',
          //       href: 'https://stackoverflow.com/questions/tagged/docusaurus',
          //     },
          //     {
          //       label: 'Discord',
          //       href: 'https://discordapp.com/invite/docusaurus',
          //     },
          //     {
          //       label: 'Twitter',
          //       href: 'https://twitter.com/docusaurus',
          //     },
          //   ],
          // },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Writeopia`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
      },
    } satisfies Preset.ThemeConfig,
};

export default config;
