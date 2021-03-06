/*
 * Copyright (c) 2017. Nuvolect LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * Contact legal@nuvolect.com for a less restrictive commercial license if you would like to use the
 * software without the GPLv3 restrictions.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.  If not,
 * see <http://www.gnu.org/licenses/>.
 *
 */

package com.nuvolect.securesuite.main;

import android.app.Activity;
import android.app.ListFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.nuvolect.securesuite.R;
import com.nuvolect.securesuite.data.MyAccounts;
import com.nuvolect.securesuite.data.MyGroups;
import com.nuvolect.securesuite.data.SqlCipher.GTTab;
import com.nuvolect.securesuite.util.AppTheme;
import com.nuvolect.securesuite.util.Cryp;
import com.nuvolect.securesuite.util.LogUtil;
import com.nuvolect.securesuite.util.Util;

import net.sqlcipher.Cursor;

public class GroupListFragment extends ListFragment{

    private static final boolean DEBUG = LogUtil.DEBUG;
    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;
    private Activity m_act;
    private GroupListCursorAdapter m_groupListCursorAdapter;
    public String m_groupTitleSelected="";
    private Cursor m_cursor = null;
    private Spinner m_accountSpinner;
    private String[] m_account_list = new String[] {};
    private String m_account="";
    private int m_account_spinner_position = -1;

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sDummyCallbacks;
    private boolean m_wasPaused;
    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        public void onGroupSelected(int id);
        public void onAccountSelected(String id);
        public void refreshFragment(String fragment_tag);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onGroupSelected(int id) { }
        @Override
        public void onAccountSelected(String id) { }
        @Override
        public void refreshFragment(String fragment_tag) { }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public GroupListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(DEBUG)LogUtil.log("GLF onCreate");

        m_act = getActivity();
        m_wasPaused = false;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if(DEBUG)LogUtil.log("GLF onDestroy");

        if( m_cursor != null && !m_cursor.isClosed())
            m_cursor.close();
        m_cursor = null;
        m_groupListCursorAdapter = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        if(DEBUG)LogUtil.log("GLF onPause");

        m_wasPaused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(DEBUG)LogUtil.log("GLF onResume");

        if( m_wasPaused){

            updateAdapter();
            m_wasPaused = false;
        }
        m_accountSpinner.setSelection( m_account_spinner_position);
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.group_list_content, container, false);

        // Add the theme background outline and fill color behind fragment
        AppTheme.applyDrawableShape(m_act, rootView);

        m_accountSpinner = (Spinner) rootView.findViewById(R.id.accountSpinner);
        ListView listView = (ListView) rootView.findViewById(android.R.id.list);

        updateAccount();
        updateAdapter();
        listView.setAdapter(m_groupListCursorAdapter);

        int h1_color = AppTheme.getThemeColor(m_act, R.attr.h1_color);
        int h2_color = AppTheme.getThemeColor(m_act, R.attr.h2_color);

        TextView all_contactsTv = (TextView) rootView.findViewById(R.id.all_contacts);
        all_contactsTv.setTextColor(h1_color);
        int nAllContacts = MyAccounts.getContactCount(m_account);
        TextView all_contacts_countTv = (TextView) rootView.findViewById(R.id.all_contacts_count);
        all_contacts_countTv.setText(Util.plural(nAllContacts, "Contact"));
        all_contacts_countTv.setTextColor( h2_color);

        TextView starred_contactsTv = (TextView) rootView.findViewById(R.id.starred_contacts);
        starred_contactsTv.setTextColor(h1_color);
        int starContacts = MyAccounts.getStarredCount(m_account);
        TextView starred_contacts_countTv = (TextView) rootView.findViewById(R.id.starred_contacts_count);
        starred_contacts_countTv.setText(Util.plural(starContacts, "Contact"));
        starred_contacts_countTv.setTextColor( h2_color);

        LinearLayout group_all_contactsLL = (LinearLayout) rootView.findViewById(R.id.group_all_contacts);
        group_all_contactsLL.setOnClickListener(onClick_allContacts);

        LinearLayout group_star_contactsLL = (LinearLayout) rootView.findViewById(R.id.group_star_contacts);
        group_star_contactsLL.setOnClickListener(onClick_starContacts);

        return rootView;
    }

    public void updateAllStarredCounts(Activity act){

        int h1_color = AppTheme.getThemeColor(m_act, R.attr.h1_color);
        int h2_color = AppTheme.getThemeColor(m_act, R.attr.h2_color);

        TextView all_contactsTv = (TextView) m_act.findViewById(R.id.all_contacts);
        all_contactsTv.setTextColor(h1_color);
        int nAllContacts = MyAccounts.getContactCount(m_account);
        TextView all_contacts_countTv = (TextView) m_act.findViewById(R.id.all_contacts_count);
        all_contacts_countTv.setText(Util.plural( nAllContacts, "Contact"));
        all_contacts_countTv.setTextColor( h2_color);

        TextView starred_contactsTv = (TextView) m_act.findViewById(R.id.starred_contacts);
        starred_contactsTv.setTextColor(h1_color);
        int starContacts = MyAccounts.getStarredCount(m_account);
        TextView starred_contacts_countTv = (TextView) m_act.findViewById(R.id.starred_contacts_count);
        starred_contacts_countTv.setText(Util.plural(starContacts, "Contact"));
        starred_contacts_countTv.setTextColor( h2_color);

        LinearLayout group_all_contactsLL = (LinearLayout) m_act.findViewById(R.id.group_all_contacts);
        group_all_contactsLL.setOnClickListener(onClick_allContacts);

        LinearLayout group_star_contactsLL = (LinearLayout) m_act.findViewById(R.id.group_star_contacts);
        group_star_contactsLL.setOnClickListener(onClick_starContacts);
    }

    /**
     * Manage the account for the list view.  Load persisted account and set as default
     * account in spinner.  Configure a spinner adapter and watch for updates.  Reset
     * Adapter and cursor when spinner is used to select a new account and groups.
     */
    public void updateAccount(){

        // Load current set of accounts
        m_account_list = MyAccounts.getAccounts();
        if( m_account_list.length == 0)
            return;

        // If first time, set default account to first account, if any
        m_account = Cryp.getCurrentAccount();
        if( m_account.isEmpty()){

            m_account = m_account_list[0];
            Cryp.setCurrentAccount( m_account);
        }
        m_account_spinner_position = findPosition( m_account_list, m_account );

        ArrayAdapter<String> spinnerArrayAdapter;
        spinnerArrayAdapter = new ArrayAdapter<String>(
                m_act, android.R.layout.simple_spinner_item, m_account_list);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        m_accountSpinner.setAdapter(spinnerArrayAdapter);
        m_accountSpinner.setSelection(m_account_spinner_position, true);

        m_accountSpinner.setOnItemSelectedListener(new OnItemSelectedListener( ) {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {

                m_account_spinner_position = position;
                m_account = m_account_list[ position];
                Cryp.setCurrentAccount( m_account);
                updateAdapter();

                updateAllStarredCounts(m_act);

                // Inform activity account was selected
                mCallbacks.onAccountSelected( m_account );
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    private int findPosition(String[] list, String target) {

        for( int i = 0; i < list.length; i++){

            if( list[i].contentEquals(target)){
                return i;
            }
        }
        return 0;
    }

    public void updateAdapter(){

        Cursor new_cursor = MyGroups.getGroupAccountCursor( m_account);

        if( m_groupListCursorAdapter == null ){

            m_groupListCursorAdapter = new GroupListCursorAdapter( m_act,
                    new_cursor,
                    0,  // flags, not using
                    R.layout.group_list_item_activated
            );
        }else{
            // Adapter in place, update the cursor, adapter will close old cursor
            m_groupListCursorAdapter.changeCursor( new_cursor);
            m_groupListCursorAdapter.notifyDataSetChanged();
        }
        // Save the cursor so it can be closed in onDestroy
        m_cursor = new_cursor;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException(
                    "Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        android.database.Cursor c = m_groupListCursorAdapter.getCursor();
//        if(DEBUG) Util.dumpCursorDescription((Cursor) c, "GLF");
        m_groupTitleSelected = c.getString(GTTab.title.ordinal());

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        //        String itemSelected = DummyContent.ITEMS.get(position).id;

        mCallbacks.onGroupSelected((int) id);
    }

    View.OnClickListener onClick_allContacts = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            mCallbacks.onGroupSelected( CConst.GROUP_ALL_IN_ACCOUNT );
        }
    };

    View.OnClickListener onClick_starContacts = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            mCallbacks.onGroupSelected( CConst.GROUP_STARRED_IN_ACCOUNT );
        }
    };

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        //FUTURE fix, crashes as content view is not yet created
//        ListView lv = getListView();
//        if( lv != null )
//            lv.setChoiceMode( activateOnItemClick ? ListView.CHOICE_MODE_SINGLE
//                        : ListView.CHOICE_MODE_NONE);
    }

    private void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }
        mActivatedPosition = position;
    }

    /**
     * Request that the adapter refresh contents.
     */
    public void notifyChanged() {

        MyGroups.loadGroupMemory();
        if( m_groupListCursorAdapter != null)
            m_groupListCursorAdapter.notifyDataSetChanged();
    }
}
