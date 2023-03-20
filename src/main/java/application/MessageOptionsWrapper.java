package application;

import com.ibm.watson.developer_cloud.assistant.v1.model.Context;

public class MessageOptionsWrapper {
    private static class Input {
        private String text;

        public String getText() {
          return text;
        }
    }
    private Context context;
    private Input input;

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  public void setInput(Input input) {
    this.input = input;
    }

    public String getText(){
        return input != null ? input.getText() : "";
    }
}