import {createStore} from "vuex";

const readStoredUser = () => {
    const storedUser = window.localStorage.getItem('user');

    if (!storedUser) {
        return { userName: '' };
    }

    try {
        return JSON.parse(storedUser);
    } catch (error) {
        console.warn('Parse stored user failed:', error);
        localStorage.removeItem('user');
        return { userName: '' };
    }
};

export default createStore({
  state: {
      user: readStoredUser()
  },
  getters: {
  },
  mutations: {//用于同步修改state中的状态
    login(state,user){
        state.user = user;
        window.localStorage.setItem('user',JSON.stringify(user));
    },
    logout(state){
        state.user={};
        localStorage.removeItem('user');
      }
  },
  actions: {
  },
  modules: {
  }
})
