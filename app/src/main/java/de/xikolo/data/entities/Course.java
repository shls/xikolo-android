package de.xikolo.data.entities;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Course implements Parcelable, Serializable {

    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("description")
    public String description;

    @SerializedName("course_code")
    public String course_code;

    @SerializedName("lecturer")
    public String lecturer;

    @SerializedName("language")
    public String language;

    @SerializedName("url")
    public String url;

    @SerializedName("visual_url")
    public String visual_url;

    @SerializedName("available_from")
    public String available_from;

    @SerializedName("available_to")
    public String available_to;

    @SerializedName("locked")
    public boolean locked;

    @SerializedName("is_enrolled")
    public boolean is_enrolled;

    @SerializedName("progress")
    public OverallProgress progress;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(id);
        parcel.writeString(name);
        parcel.writeString(description);
        parcel.writeString(course_code);
        parcel.writeString(lecturer);
        parcel.writeString(language);
        parcel.writeString(url);
        parcel.writeString(visual_url);
        parcel.writeString(available_from);
        parcel.writeString(available_to);
        parcel.writeByte((byte) (locked ? 1 : 0));
        parcel.writeByte((byte) (is_enrolled ? 1 : 0 ));
        parcel.writeParcelable(progress, i);
    }

    public Course() {
        progress = new OverallProgress();
    }

    public Course(Parcel in) {
        id = in.readString();
        name = in.readString();
        description = in.readString();
        course_code = in.readString();
        lecturer = in.readString();
        language = in.readString();
        url = in.readString();
        visual_url = in.readString();
        available_from = in.readString();
        available_to = in.readString();
        locked = in.readByte() != 0;
        is_enrolled = in.readByte() != 0;
        progress = in.readParcelable(Course.class.getClassLoader());
    }

    public static final Parcelable.Creator<Course> CREATOR = new Parcelable.Creator<Course>() {
        public Course createFromParcel(Parcel in) {
            return new Course(in);
        }

        public Course[] newArray(int size) {
            return new Course[size];
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (((Object) this).getClass() != obj.getClass())
            return false;
        Course o = (Course) obj;
        if (id == null) {
            if (o.id != null)
                return false;
        } else if (!id.equals(o.id))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 11;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

}
