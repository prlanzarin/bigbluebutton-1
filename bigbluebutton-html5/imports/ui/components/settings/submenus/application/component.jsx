import React from 'react';
import Modal from 'react-modal';
import Icon from '/imports/ui/components/icon/component';
import Button from '/imports/ui/components/button/component';
import BaseMenu from '../base/component';
import ReactDOM from 'react-dom';
import cx from 'classnames';
import styles from '../styles.scss';
import Toggle from '/imports/ui/components/switch/component';
import { defineMessages, injectIntl } from 'react-intl';

const MIN_FONTSIZE = 0;
const MAX_FONTSIZE = 4;

const intlMessages = defineMessages({
  applicationSectionTitle: {
    id: 'app.submenu.application.applicationSectionTitle',
    description: 'Application section title',
  },
  audioNotifyLabel: {
    id: 'app.submenu.application.audioNotifyLabel',
    description: 'audio notification label',
  },
  pushNotifyLabel: {
    id: 'app.submenu.application.pushNotifyLabel',
    description: 'push notifiation label',
  },
  fontSizeControlLabel: {
    id: 'app.submenu.application.fontSizeControlLabel',
    description: 'label for font size ontrol',
  },
  increaseFontBtnLabel: {
    id: 'app.submenu.application.increaseFontBtnLabel',
    description: 'label for button to increase font size',
  },
  increaseFontBtnDesc: {
    id: 'app.submenu.application.increaseFontBtnDesc',
    description: 'adds descriptive context to increase font size button',
  },
  decreaseFontBtnLabel: {
    id: 'app.submenu.application.decreaseFontBtnLabel',
    description: 'label for button to reduce font size',
  },
  decreaseFontBtnDesc: {
    id: 'app.submenu.application.decreaseFontBtnDesc',
    description: 'adds descriptive context to decrease font size button',
  },
  languageLabel: {
    id: 'app.submenu.application.languageLabel',
    description: 'displayed label for changing application locale',
  },
  ariaLanguageLabel: {
    id: 'app.submenu.application.ariaLanguageLabel',
    description: 'aria label for locale change section',
  },
  languageOptionLabel:  {
    id: 'app.submenu.application.languageOptionLabel',
    description: 'default change language option when locales are available',
  },
  noLocaleOptionLabel: {
    id: 'app.submenu.application.noLocaleOptionLabel',
    description: 'default change language option when no locales available',
  },
});

class ApplicationMenu extends BaseMenu {
  constructor(props) {
    super(props);

    this.state = {
      settingsName: 'application',
      settings: props.settings,
    };
  }

  handleUpdateFontSize(size) {
    let obj = this.state;
    obj.settings.fontSize = size;
    this.handleUpdateSettings(this.state.settingsName, obj.settings);
  }

  setHtmlFontSize(size) {
    document.getElementsByTagName('html')[0].style.fontSize = size;
  };

  changeFontSize(size) {
    let obj = this.state;
    obj.settings.fontSize = size;
    this.setState(obj, () => {
      this.setHtmlFontSize(this.state.settings.fontSize);
      this.handleUpdateFontSize(this.state.settings.fontSize);
    });
  }

  handleIncreaseFontSize() {
    const currentFontSize = this.state.settings.fontSize;
    const availableFontSizes = this.props.fontSizes;
    const canIncreaseFontSize = availableFontSizes.indexOf(currentFontSize) < MAX_FONTSIZE;
    let fs = (canIncreaseFontSize) ? availableFontSizes.indexOf(currentFontSize) + 1 : MAX_FONTSIZE;
    this.changeFontSize(availableFontSizes[fs]);
  };

  handleDecreaseFontSize() {
    const currentFontSize = this.state.settings.fontSize;
    const availableFontSizes = this.props.fontSizes;
    const canDecreaseFontSize = availableFontSizes.indexOf(currentFontSize) > MIN_FONTSIZE;
    let fs = (canDecreaseFontSize) ? availableFontSizes.indexOf(currentFontSize) - 1 : MIN_FONTSIZE;
    this.changeFontSize(availableFontSizes[fs]);
  };

  handleSelectChange(fieldname, options, e) {
    let obj = this.state;
    obj.settings[fieldname] = e.target.value.toLowerCase().replace('_', '-');
    this.handleUpdateSettings('application', obj.settings);
  }

  render() {
    const {
      availableLocales,
      intl,
    } = this.props;

    return (
      <div className={styles.tabContent}>
        <div className={styles.header}>
          <h3 className={styles.title}>
            {intl.formatMessage(intlMessages.applicationSectionTitle)}
          </h3>
        </div>
        <div className={styles.form}>
          <div className={styles.row}>
            <div className={styles.col} aria-hidden="true">
              <div className={styles.formElement}>
                <label className={styles.label}>
                  {intl.formatMessage(intlMessages.audioNotifyLabel)}
                </label>
              </div>
            </div>
            <div className={styles.col}>
              <div className={cx(styles.formElement, styles.pullContentRight)}>
                <Toggle
                  icons={false}
                  defaultChecked={this.state.settings.chatAudioNotifications}
                  onChange={() => this.handleToggle('chatAudioNotifications')}
                  ariaLabelledBy={'audioNotify'}
                  ariaLabel={intl.formatMessage(intlMessages.audioNotifyLabel)} />
              </div>
            </div>
          </div>
          <div className={styles.row}>
            <div className={styles.col}>
              <div className={styles.formElement} >
                <label className={styles.label}>
                  {intl.formatMessage(intlMessages.pushNotifyLabel)}
                </label>
              </div>
            </div>
            <div className={styles.col}>
              <div className={cx(styles.formElement, styles.pullContentRight)}>
                <Toggle
                  icons={false}
                  defaultChecked={this.state.settings.chatPushNotifications}
                  onChange={() => this.handleToggle('chatPushNotifications')}
                  ariaLabelledBy={'pushNotify'}
                  ariaLabel={intl.formatMessage(intlMessages.pushNotifyLabel)}/>
              </div>
            </div>
          </div>
          <div className={styles.row}>
            <div className={styles.col}>
              <div className={styles.formElement}>
                <label className={styles.label}>
                  {intl.formatMessage(intlMessages.languageLabel)}
                </label>
              </div>
            </div>
            <div className={styles.col}>
              <div className={cx(styles.formElement, styles.pullContentRight)}
              aria-labelledby="changeLangLabel">
                <select
                  defaultValue={this.state.settings.locale}
                  className={styles.select}
                  onChange={this.handleSelectChange.bind(this, 'locale', availableLocales)}>
                  <option>
                    { availableLocales &&
                      availableLocales.length ?
                      intl.formatMessage(intlMessages.languageOptionLabel) :
                      intl.formatMessage(intlMessages.noLocaleOptionLabel) }
                  </option>
                {availableLocales ? availableLocales.map((locale, index) =>
                  <option key={index} value={locale.locale}>
                    {locale.name}
                  </option>
                ) : null }
                </select>
              </div>
              <div id="changeLangLabel" aria-label={intl.formatMessage(intlMessages.ariaLanguageLabel)}></div>
            </div>
          </div>
          <hr className={styles.separator}/>
          <div className={styles.row}>
            <div className={styles.col}>
              <div className={styles.formElement}>
                <label className={styles.label}>
                  {intl.formatMessage(intlMessages.fontSizeControlLabel)}
                </label>
              </div>
            </div>
            <div className={styles.col}>
              <div className={cx(styles.formElement, styles.pullContentCenter)}>
                <label className={cx(styles.label, styles.bold)}>
                  {this.state.settings.fontSize}
                </label>
              </div>
            </div>
            <div className={styles.col}>
              <div className={cx(styles.formElement, styles.pullContentRight)}>
                <div className={styles.pullContentRight}>
                  <div className={styles.col}>
                    <Button
                      onClick={() => this.handleIncreaseFontSize()}
                      color={'success'}
                      icon={'add'}
                      circle={true}
                      hideLabel={true}
                      label={intl.formatMessage(intlMessages.increaseFontBtnLabel)}
                    />
                  </div>
                  <div className={styles.col}>
                    <Button
                      onClick={() => this.handleDecreaseFontSize()}
                      color={'success'}
                      icon={'substract'}
                      circle={true}
                      hideLabel={true}
                      label={intl.formatMessage(intlMessages.decreaseFontBtnLabel)}
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
};

export default injectIntl(ApplicationMenu);
