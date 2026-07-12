package ru.tzfviewer;

final class UpdateButtonState {
    enum Action { CHECK, DOWNLOAD, INSTALL, NONE }
    final String label;
    final boolean enabled;
    final Action action;
    private UpdateButtonState(String label,boolean enabled,Action action){this.label=label;this.enabled=enabled;this.action=action;}
    static UpdateButtonState check(){return new UpdateButtonState("Проверить обновление",true,Action.CHECK);}
    static UpdateButtonState checking(){return new UpdateButtonState("Проверяем…",false,Action.NONE);}
    static UpdateButtonState download(String version){return new UpdateButtonState("Скачать "+version,true,Action.DOWNLOAD);}
    static UpdateButtonState progress(int percent){return new UpdateButtonState(percent<0?"Загрузка…":percent+"%",false,Action.NONE);}
    static UpdateButtonState install(String version){return new UpdateButtonState(version==null?"Установить обновление":"Установить "+version,true,Action.INSTALL);}
    static UpdateButtonState retry(){return new UpdateButtonState("Повторить",true,Action.CHECK);}
}
