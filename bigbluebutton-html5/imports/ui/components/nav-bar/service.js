import Auth from '/imports/ui/services/auth';
import Breakouts from '/imports/api/breakouts';
import Meetings from '/imports/api/meetings';


const getBreakouts = () => Breakouts.find().fetch();

const getBreakoutJoinURL = (breakout) => {
  const currentUserId = Auth.userID;

  if (breakout.users) {
    const user = breakout.users.find(u => u.userId === currentUserId);

    if (user) {
      const urlParams = user.urlParams;
      return [
        window.origin,
        `html5client/join?sessionToken=${urlParams.sessionToken}`,
      ].join('/');
    }
  }
  return '';
};

const isNavBarEnabled = (id) => {
  const meeting = Meetings.findOne({ meetingId: id });
  let navBarEnabled = true;

  if (meeting.metadataProp !== 'undefined' && meeting.metadataProp.metadata.html5navbar !=='undefined' && meeting.metadataProp.metadata.html5navbar !== null) {
    navBarEnabled = meeting.metadataProp.metadata.html5navbar;
  }
  else {
    navBarEnabled = Meteor.settings.public.navBar.enabled;
  }

  return navBarEnabled;
};

export default {
  getBreakouts,
  getBreakoutJoinURL,
  isNavBarEnabled,
};
